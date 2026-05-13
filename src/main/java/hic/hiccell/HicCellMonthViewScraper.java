package hic.hiccell;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HicCellMonthViewScraper {

    private static final String MONTH_VIEW_URL = "https://pathbio.med.upenn.edu/hic/hiccell/CellRequests/month_view";
    private static final int MAX_PAGES_PER_TAB = 100;

    private final Path browserProfilePath;

    public HicCellMonthViewScraper() {
        this(Path.of(System.getProperty("user.home"), ".hic-studio-browser-profile"));
    }

    HicCellMonthViewScraper(Path browserProfilePath) {
        this.browserProfilePath = browserProfilePath;
    }

    public List<HicCellOrderRecord> fetchLiveCells() {
        return fetchTab("Live Cells", false);
    }

    public List<HicCellOrderRecord> fetchCancelled() {
        return fetchTab("Cancelled", true);
    }

    public HicCellMonthViewRows fetchLiveCellsAndCancelled() {
        try (Playwright playwright = Playwright.create()) {
            clearStaleChromeSingletonLocks();
            BrowserContext context = playwright.chromium().launchPersistentContext(
                    browserProfilePath,
                    new com.microsoft.playwright.BrowserType.LaunchPersistentContextOptions()
                            .setChannel("chrome")
                            .setHeadless(false)
            );
            context.setDefaultTimeout(120_000);
            blockNonEssentialResources(context);
            try {
                Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
                page.setDefaultNavigationTimeout(120_000);
                page.navigate(MONTH_VIEW_URL);
                page.waitForLoadState();

                clickTab(page, "Live Cells");
                page.waitForLoadState();
                waitForVisibleTableRows(page);
                List<HicCellOrderRecord> liveCells = readAllPages(page, false);

                clickTab(page, "Cancelled");
                page.waitForLoadState();
                waitForVisibleTableRows(page);
                List<HicCellOrderRecord> cancelled = readAllPages(page, true);

                return new HicCellMonthViewRows(liveCells, cancelled);
            } finally {
                context.close();
            }
        }
    }

    private List<HicCellOrderRecord> fetchTab(String tabName, boolean cancelled) {
        try (Playwright playwright = Playwright.create()) {
            clearStaleChromeSingletonLocks();
            BrowserContext context = playwright.chromium().launchPersistentContext(
                    browserProfilePath,
                    new com.microsoft.playwright.BrowserType.LaunchPersistentContextOptions()
                            .setChannel("chrome")
                            .setHeadless(false)
            );
            context.setDefaultTimeout(120_000);
            blockNonEssentialResources(context);
            try {
                Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
                page.setDefaultNavigationTimeout(120_000);
                page.navigate(MONTH_VIEW_URL);
                page.waitForLoadState();
                clickTab(page, tabName);
                page.waitForLoadState();
                waitForVisibleTableRows(page);
                return readAllPages(page, cancelled);
            } finally {
                context.close();
            }
        }
    }

    private void clearStaleChromeSingletonLocks() {
        deleteIfExists(browserProfilePath.resolve("SingletonLock"));
        deleteIfExists(browserProfilePath.resolve("SingletonSocket"));
        deleteIfExists(browserProfilePath.resolve("SingletonCookie"));
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Chrome will report a clearer profile-in-use error if this is an active lock.
        }
    }

    private void blockNonEssentialResources(BrowserContext context) {
        context.route("**/*", route -> {
            String resourceType = route.request().resourceType();
            if ("image".equals(resourceType) || "media".equals(resourceType) || "font".equals(resourceType)) {
                route.abort();
            } else {
                route.resume();
            }
        });
    }

    private void clickTab(Page page, String tabName) {
        Locator roleTab = page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(tabName));
        if (roleTab.count() > 0) {
            roleTab.first().click();
            waitForTabSettled(page);
            return;
        }

        Locator textTab = page.locator("a, button, li").filter(new Locator.FilterOptions().setHasText(tabName));
        if (textTab.count() > 0) {
            textTab.first().click();
            waitForTabSettled(page);
            return;
        }

        page.evaluate("""
                tabName => {
                  const candidates = Array.from(document.querySelectorAll('a, button, li'));
                  const el = candidates.find(node => (node.textContent || '').trim() === tabName);
                  if (el) el.click();
                }
                """, tabName);
        waitForTabSettled(page);
    }

    private void waitForTabSettled(Page page) {
        page.waitForTimeout(250);
    }

    private List<HicCellOrderRecord> readAllPages(Page page, boolean cancelled) {
        List<HicCellOrderRecord> apiRecords = readDataTableRowsFromApi(page, cancelled);
        if (!apiRecords.isEmpty()) {
            return apiRecords;
        }

        setVisibleTablePageLength(page, 50);

        List<HicCellOrderRecord> records = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int pageCount = activeDataTablePageCount(page);
        int pageIndex = 0;

        while (pageIndex < MAX_PAGES_PER_TAB) {
            if (!goToDataTablePage(page, pageIndex)) {
                if (pageIndex == 0) {
                    waitForVisibleTableRows(page);
                } else if (!goToNextPageByDom(page)) {
                    break;
                }
            }
            Locator rows = page.locator("table:visible tbody tr");
            int rowCount = rows.count();
            for (int i = 0; i < rowCount; i++) {
                HicCellOrderRecord record = parseRow(rows.nth(i), cancelled);
                if (record == null) {
                    continue;
                }
                String key = record.requestId() + "|" + record.collectionDate() + "|" + record.orderedBy()
                        + "|" + record.cellType() + "|" + record.delivered() + "|" + record.cancelled();
                if (seen.add(key)) {
                    records.add(record);
                }
            }

            pageIndex++;
            if (pageCount > 1) {
                if (pageIndex >= pageCount) {
                    break;
                }
            } else if (!hasNextPage(page)) {
                break;
            }
        }

        return records;
    }

    private List<HicCellOrderRecord> readDataTableRowsFromApi(Page page, boolean cancelled) {
        Object value = page.evaluate("""
                () => {
                  try {
                    if (!window.jQuery || !jQuery.fn.DataTable) return [];
                    const table = Array.from(document.querySelectorAll('table')).find(el => !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length));
                    if (!table || !jQuery.fn.DataTable.isDataTable(table)) return [];
                    const api = jQuery(table).DataTable();
                    return api.rows({ search: 'applied' }).data().toArray().map(row => {
                      const cells = Array.isArray(row) ? row : Object.values(row);
                      return cells.map(cell => {
                        const div = document.createElement('div');
                        div.innerHTML = String(cell == null ? '' : cell);
                        return (div.textContent || div.innerText || '').trim().replace(/\\s+/g, ' ');
                      });
                    });
                  } catch (e) {
                    return [];
                  }
                }
                """);
        if (!(value instanceof List<?> rows)) {
            return List.of();
        }

        List<HicCellOrderRecord> records = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object rowValue : rows) {
            if (!(rowValue instanceof List<?> cells)) {
                continue;
            }
            List<String> cellTexts = new ArrayList<>();
            for (Object cell : cells) {
                cellTexts.add(cell == null ? "" : cell.toString());
            }

            HicCellOrderRecord record = parseCells(cellTexts, cancelled);
            if (record == null) {
                continue;
            }
            String key = record.requestId() + "|" + record.collectionDate() + "|" + record.orderedBy()
                    + "|" + record.cellType() + "|" + record.delivered() + "|" + record.cancelled()
                    + "|" + record.cancellationReason();
            if (seen.add(key)) {
                records.add(record);
            }
        }
        return records;
    }

    private int activeDataTablePageCount(Page page) {
        Object value = page.evaluate("""
                () => {
                  try {
                  if (!window.jQuery || !jQuery.fn.DataTable) return 1;
                  const table = Array.from(document.querySelectorAll('table')).find(el => !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length));
                  if (!table) return 1;
                  if (!jQuery.fn.DataTable.isDataTable(table)) return 1;
                  const api = jQuery(table).DataTable();
                  const info = api.page.info();
                  return info && info.pages ? info.pages : 1;
                  } catch (e) {
                    return 1;
                  }
                }
                """);
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        try {
            return Math.max(1, Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private boolean goToDataTablePage(Page page, int pageIndex) {
        Object changed = page.evaluate("""
                targetPage => {
                  try {
                  if (!window.jQuery || !jQuery.fn.DataTable) return false;
                  const table = Array.from(document.querySelectorAll('table')).find(el => !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length));
                  if (!table) return false;
                  if (!jQuery.fn.DataTable.isDataTable(table)) return false;
                  const api = jQuery(table).DataTable();
                  if (api.page() === targetPage) return false;
                  api.page(targetPage).draw(false);
                  return true;
                  } catch (e) {
                    return false;
                  }
                }
                """, pageIndex);
        if (Boolean.TRUE.equals(changed)) {
            waitForDataTablePage(page, pageIndex);
            return true;
        }
        return pageIndex == 0;
    }

    private void waitForDataTablePage(Page page, int pageIndex) {
        try {
            page.waitForFunction("""
                    targetPage => {
                      try {
                      if (!window.jQuery || !jQuery.fn.DataTable) return true;
                      const table = Array.from(document.querySelectorAll('table')).find(el => !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length));
                      if (!table) return true;
                      if (!jQuery.fn.DataTable.isDataTable(table)) return true;
                      return jQuery(table).DataTable().page() === targetPage;
                      } catch (e) {
                        return true;
                      }
                    }
                    """, pageIndex, new Page.WaitForFunctionOptions().setTimeout(1000));
        } catch (RuntimeException ignored) {
            page.waitForTimeout(100);
        }
    }

    private boolean goToNextPageByDom(Page page) {
        Locator next = page.locator(".dataTables_paginate:visible .next:not(.disabled), .dataTables_paginate:visible a.paginate_button.next:not(.disabled)");
        if (next.count() == 0) {
            return false;
        }
        String before = activeTableText(page);
        next.first().evaluate("element => element.click()");
        waitForTableTextChange(page, before);
        return true;
    }

    private boolean hasNextPage(Page page) {
        return page.locator(".dataTables_paginate:visible .next:not(.disabled), .dataTables_paginate:visible a.paginate_button.next:not(.disabled)").count() > 0;
    }

    private void waitForTableTextChange(Page page, String previousTableText) {
        try {
            page.waitForFunction(
                    "previous => { const table = Array.from(document.querySelectorAll('table')).find(el => !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length)); return table && table.innerText !== previous; }",
                    previousTableText,
                    new Page.WaitForFunctionOptions().setTimeout(1000)
            );
        } catch (RuntimeException ignored) {
            page.waitForTimeout(100);
        }
    }

    private void waitForVisibleTableRows(Page page) {
        page.waitForSelector("table:visible tbody tr");
    }

    private void setVisibleTablePageLength(Page page, int pageLength) {
        Object changed = page.evaluate("""
                pageLength => {
                  try {
                    const selects = Array.from(document.querySelectorAll('.dataTables_length select, select[name$="_length"]'));
                    const select = selects.find(el => !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length));
                    if (select) {
                      const option = Array.from(select.options).find(opt => opt.value === String(pageLength) || opt.textContent.trim() === String(pageLength));
                      if (option) {
                        select.value = option.value;
                        select.dispatchEvent(new Event('change', { bubbles: true }));
                        if (window.jQuery) jQuery(select).trigger('change');
                        return true;
                      }
                    }

                    if (window.jQuery && jQuery.fn.DataTable) {
                      const table = Array.from(document.querySelectorAll('table')).find(el => !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length));
                      if (table && jQuery.fn.DataTable.isDataTable(table)) {
                        const api = jQuery(table).DataTable();
                        api.page.len(pageLength).draw(false);
                        return true;
                      }
                    }

                    return false;
                  } catch (e) {
                    return false;
                  }
                }
                """, pageLength);

        if (Boolean.TRUE.equals(changed)) {
            waitForPageLength(page, pageLength);
            waitForVisibleTableRows(page);
        }
    }

    private void waitForPageLength(Page page, int pageLength) {
        try {
            page.waitForFunction("""
                    pageLength => {
                      const selects = Array.from(document.querySelectorAll('.dataTables_length select, select[name$="_length"]'));
                      const select = selects.find(el => !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length));
                      if (select && select.value === String(pageLength)) return true;
                      if (!window.jQuery || !jQuery.fn.DataTable) return false;
                      const table = Array.from(document.querySelectorAll('table')).find(el => !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length));
                      return table && jQuery.fn.DataTable.isDataTable(table) && jQuery(table).DataTable().page.len() === pageLength;
                    }
                    """, pageLength, new Page.WaitForFunctionOptions().setTimeout(1500));
        } catch (RuntimeException ignored) {
            page.waitForTimeout(250);
        }
    }

    private String activeTableText(Page page) {
        Object value = page.evaluate("""
                () => {
                  const table = Array.from(document.querySelectorAll('table')).find(el => !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length));
                  return table ? table.innerText : '';
                }
                """);
        return value == null ? "" : value.toString();
    }

    private HicCellOrderRecord parseRow(Locator row, boolean cancelled) {
        Locator cells = row.locator("td");
        int count = cells.count();
        int expectedCells = cancelled ? 9 : 8;
        if (count < expectedCells) {
            return null;
        }

        try {
            int requestId = parseRequestId(cells.nth(0).textContent());
            LocalDate collectionDate = LocalDate.parse(clean(cells.nth(1).textContent()));
            String orderedBy = clean(cells.nth(2).textContent());
            String labOwner = clean(cells.nth(3).textContent());
            String cellType = clean(cells.nth(4).textContent());
            double requested = parseDouble(cells.nth(5).textContent());
            double minimum = parseDouble(cells.nth(6).textContent());
            String cancellationReason = cancelled ? clean(cells.nth(7).textContent()) : "";
            int deliveredColumn = cancelled ? 8 : 7;
            double delivered = parseDouble(cells.nth(deliveredColumn).textContent());

            return new HicCellOrderRecord(requestId, collectionDate, orderedBy, labOwner, cellType,
                    requested, minimum, delivered, cancelled, cancellationReason);
        } catch (NumberFormatException | DateTimeParseException ex) {
            return null;
        }
    }

    private HicCellOrderRecord parseCells(List<String> cells, boolean cancelled) {
        int expectedCells = cancelled ? 9 : 8;
        if (cells == null || cells.size() < expectedCells) {
            return null;
        }

        try {
            int requestId = parseRequestId(cells.get(0));
            LocalDate collectionDate = LocalDate.parse(clean(cells.get(1)));
            String orderedBy = clean(cells.get(2));
            String labOwner = clean(cells.get(3));
            String cellType = clean(cells.get(4));
            double requested = parseDouble(cells.get(5));
            double minimum = parseDouble(cells.get(6));
            String cancellationReason = cancelled ? clean(cells.get(7)) : "";
            int deliveredColumn = cancelled ? 8 : 7;
            double delivered = parseDouble(cells.get(deliveredColumn));

            return new HicCellOrderRecord(requestId, collectionDate, orderedBy, labOwner, cellType,
                    requested, minimum, delivered, cancelled, cancellationReason);
        } catch (NumberFormatException | DateTimeParseException ex) {
            return null;
        }
    }

    private int parseRequestId(String value) {
        String digits = clean(value).replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            throw new NumberFormatException("Missing request id");
        }
        return Integer.parseInt(digits);
    }

    private double parseDouble(String value) {
        String cleaned = clean(value).replace(",", "");
        if (cleaned.isEmpty()) {
            return 0.0;
        }
        return Double.parseDouble(cleaned);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
