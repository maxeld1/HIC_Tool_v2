# Cancellation Decision

The **Cancellation Decision** button opens a modal planner from a saved donor folder.
It produces a reviewable recommendation and an optional text export. It never
submits cancellations to HIC Cell or changes label/sign-out exports.

For the full offline, searchable instruction booklet, open **Help → User Guide**
or press **F1**. Its topic index includes the daily workflow, settings, unique IDs,
priority rules, linked requests, saved outcomes, workbook history, and troubleshooting.

## Daily workflow

Before assigning the program's sequential unique IDs, pasted requests are sorted
by numeric website request ID (ascending), then within each request ID by:
Total T, CD4, CD8, Monocytes, NK Cells, PBMC, Unpurified Apheresis Product,
Top layer of Ficoll, Bottom pellet of Ficoll. Unlisted types, including B Cells,
follow those types alphabetically. Existing internal cell-type names are retained.
The same set of valid requests therefore receives the same unique IDs regardless
of its pasted order. Adding/removing requests can shift sequential IDs. Preview
edits to request IDs or cell types are normalized again before generating outputs;
the preview is refreshed to match. Other output-specific sorts preserve these IDs.
When adopting this ordering, regenerate related labels/sheets together; do not mix
older generated IDs with newly generated ones. Keep each request's comment and
continuation/history lines attached when copying the source.

The planner is organized into three numbered steps with Back / Next navigation:
**Yields → Review Dependencies → Decisions & Outcomes**.
You can revisit any step. Changing inputs clears stale recommendations.
The opening chooser lists up to twelve recent donor folders from the configured
output directory (newest collection date first), with a Browse option for others.

1. At the beginning of the day, paste the complete request list, enter the donor
   number, **Parse & Preview**, and **Perform All Actions** as usual. **Make Labels**
   also produces the necessary priority workbook. Configure recognized name headers
   on Lab Members and request ID/date/requester headers on Today's Requests.
2. Later, click **Cancellation Decision** and select the generated donor/date folder,
   such as `ND123_2026-10-23`. Confirm the donor, collection date, order count, and
   generation time read from its `Low_Yield_Order_Priority.xlsx`. You do not need
   to paste the orders or re-enter the donor number in the main window.
3. Opening the planner is offline. It restores orders, comments, request quantities,
   lab-member/priority-request protections, and historical fulfillment from a
   hidden **Cancellation Inputs** sheet in that workbook. The saved data uses the
   same comparator as the visible priority sheets. It does not re-fetch the sheets
   or scrape historical fulfillment. An incomplete protection snapshot is rejected;
   unavailable history is explicitly marked for manual weekly review.
4. In **1 Yields**, mark each available cell type **Ready** and enter its total
   usable yield. Unchecked types are **Not ready / unknown**; their retained input
   values are ignored. A checked Ready field requires a number, including zero
   when there are no cells. Use exactly the same
   units as the request amounts (for example, millions of cells if Max/Min are in
   millions). Not ready means unknown; zero means no yield. Product volumes
   must remain in their own request units; the planner does not convert volumes
   into cell yields or model shared upstream apheresis consumption.
   If a cell type will not be isolated at all, check **Not isolating today** instead.
   This disables Ready/yield entry and supplies known zero availability, not an
   unknown yield. Its orders and linked all-or-none partners cannot be recommended
   for fulfillment, even if the other cell yields are sufficient. Cancellable
   groups receive CANCEL; protected or already committed groups require REVIEW.
   Reasons and exported notes identify the unavailable cell type. Cell types
   marked Not isolating today count as entered zero yields in Who to Cancel and
   the workbook; unentered linked partners remain review-only until their yields
   are entered. Unlinked orders for other cell types remain eligible.
   If this cell type already has recorded deliveries, use Ready with total usable
   yield instead; the contradictory Not isolating setting is rejected.
   Unchecking Not isolating returns the type to unknown until Ready is selected.
   These availability choices, like yield inputs, must be set again after reopening.
5. In **2 Review Dependencies**, review every comment and its suggested **Linked Group**. Give all orders that
   must be fulfilled together the same group value. CD4/CD8 requests from the same
   requester are linked and cannot be split in the planner. Conditional comments
   suggest linking all of that requester's rows; narrow or expand these suggestions
   to match the experiment. This is heuristic text matching, not full language
   understanding. Review comments that the matching did not recognize as well.
6. Review the **Donor Specific** and **Filled This Week** flags. Generic urgency
   requests do not automatically raise priority. Donor/HLA-specific language can
   suggest a donor-specific flag, which can be corrected manually.
7. In step 2, verify donor/date, outcomes, flagged comments, and missing history.
   Use individual checkboxes or **Select All** to acknowledge every current item
   and the donor/date verification together. Select All is only a shortcut after
   review: it does not establish that the data is correct. Hover over flagged
   comments for their full text and current linked group. Editing orders resets
   the acknowledgements.
8. Calculate, then use **3 Decisions & Outcomes → Who to Cancel**. Recommended
   cancellations have individual **Will Cancel** checkboxes, initially unchecked.
   Check the ones you intend to cancel; leave the others unchecked. **Select All
   Listed** checks the current list as a shortcut.
9. Use **Select Other Orders…** to add any other pending orders, including
   nonrecommended orders and cell types without entered yields. Added orders are
   checked in the same Who to Cancel table and labelled MANUAL SELECTION.
   Protected orders are identified; manual inclusion is not a planner recommendation.
10. After the selected cancellations actually happen, click **Confirm & Save
    Selected Cancellations…** and review the named donor/date/order list. Confirming
    saves actual CANCELLED outcomes with zero delivered. Unchecked orders remain
    unchanged, NOT fulfilled. No website action is performed.
11. Use **Recorded Outcomes & Deliveries**, within the same Decisions & Outcomes
    step, to record actual deliveries or correct previous outcomes. FULFILLED
    requires a positive delivered amount; other statuses require zero. Use
    **Save Recorded Outcomes**. Review dependencies and recalculate after changes.

The cancellation planner never visits the website. Morning historical priority
generation is unchanged. Changing inputs or recalculating clears cancellation
selections and the previous displayed recommendation. Checkbox selections are
temporary intent, not saved outcomes. Close warns if selected cancellations
have not been confirmed.

## Reading the recommendation

**3 Decisions & Outcomes** starts with a per-cell inventory summary: total yield,
already delivered, planned/provisionally reserved, and remaining. Reservations
are not proof that a request can be distributed; check HOLD / REVIEW statuses.
The result views are:

- **Who to Cancel**: actionable scoped cancellation recommendations.
- **Allocations & Linked Groups**: every order, allocation, reason, and comment.
  Select a row to inspect all requests in its linked group in the lower pane.
- **Needs Your Decision**: HOLD / REVIEW rows, unknown-yield linked cancellation
  consequences, and unresolved issues. Resolve these before distributing.
- **Changes Since Last Calculation**: changed decisions/allocations compared with
  the previous calculation in this open session. Across sessions, use the workbook's
  cumulative **Decision History** instead.
- **Full Details**: complete report, including unmet weekly-access targets.

**Recorded Outcomes & Deliveries** shows cumulative donor/day fulfilled,
cancelled, and pending counts plus an unsaved-changes indicator. It lives alongside
Who to Cancel, allocations, and review details, not in a separate workflow step.
Workbook generation remains in the background; Open Highlighted Priority Sheet
enables after saving.

To review previously confirmed cancellations, reopen the same donor/day folder
and go to **3 Decisions & Outcomes → Recorded Outcomes & Deliveries**. Rows with
**Recorded Status = CANCELLED** are the saved actual cancellations, including
ones confirmed during earlier sessions. They are excluded from new recommendations
in Who to Cancel. Outcomes are local: use the same output directory and preserve
its sibling CancellationDecisionState folder. This view shows current recorded
statuses, not an immutable audit trail of later corrections.

The green **Confirm & Save Selected Cancellations…** button records actual
cancellations. The workbook's **Decision History** instead preserves calculation
iterations; a recommendation there is not proof a cancellation was confirmed.

After calculation, **Who to Cancel** shows a sortable table of requesters, order
numbers, cell types, requested quantities, reasons, and requester comments. Hover
over a comment to read its full text; the table scrolls horizontally. Its initial list includes only pending
orders recommended for cancellation **for cell types whose yield field was filled
in**. An entered zero yield is included; a blank yield is excluded. Already recorded
cancellations and protected orders are not listed as new cancellations.

Linked consequences for cell types without entered yields remain visible as review
notes in **Full Decision Details**; they are not shown as instructions to cancel
those cell types. Unresolved shortages are prominently flagged above the table.

Each donor/day uses one `Cancellation_Priority_<donor>_<date>.xlsx` inside the
selected donor folder. Each calculation appends a numbered iteration to its
**Decision History** tab, retaining earlier outcomes, yields, comments, reasons,
and unresolved issues. The summary and highlighted priority tabs show the latest
iteration. **Open Highlighted Priority Sheet** opens this same workbook after
saving. Close the workbook in Excel before recalculating, then reopen it to see the
new iteration. Windows may prevent saving while it is open; other systems may
keep displaying the previously opened version until it is reopened.
The original `Low_Yield_Order_Priority.xlsx` is preserved. Older randomly named
cancellation exports are left as archives; they are not merged automatically.

- **Cancellation Summary** opens first and lists the same scoped cancellations.
- The copied priority tabs retain their ranks and add Decision, Planned Allocation,
  Decision Reason, and Requester Comment columns. Pink rows marked **SHOULD CANCEL** identify pending
  cancellation recommendations for entered yields. **REVIEW** identifies unresolved
  orders; original priority highlights remain, so always read the Decision column.
- Rows without entered yields are explicitly marked **NO YIELD ENTERED**, or
  **LINKED — NO YIELD ENTERED** when a dependency has implications for that row.
- **Export Detailed Notes** remains available for the full text report.

Changing yields or other inputs clears the displayed cancellation list and disables
the workbook button until recalculation. Earlier iterations remain in Decision
History; these are recommendations, not proof of actual cancellations. Saving a
new iteration uses a temporary file before replacing the existing workbook, so a
failed save does not discard its prior history.

## Early PBMC distributions

1. Calculate with PBMC yield Ready and later yields Not ready. Linked unknown
   yields still produce HOLD / review results, not approval to distribute.
2. In Who to Cancel, check Will Cancel on the recommended PBMC orders you choose
   to cancel. Select Other Orders can add other pending PBMCs or other cell types.
   Once the cancellations actually happen, confirm and save the checked orders.
3. Record actual PBMC deliveries and their positive delivered amounts under
   Recorded Outcomes & Deliveries in the same step, then save. Unchecked does not
   mean fulfilled. Include every PBMC request in the morning input; regenerate
   the snapshot if orders are missing.
4. Later, enter isolated-cell yields, review dependencies, and recalculate.
   Recorded cancelled PBMCs propagate to linked pending requests unless protection
   or prior fulfillment conflicts; those cases require review. Recorded fulfilled
   members commit their linked requests against automatic cancellation.

Only pending orders can be newly selected. Correct previously completed outcomes
under Recorded Outcomes & Deliveries. Cancelling the selection/confirmation
dialog leaves recorded outcomes unchanged. Saving actual cancellations clears the
old recommendation; recalculate to append the next workbook iteration.

Saved outcomes are restored for the same donor/day. Enter total usable yields,
including already distributed quantities; recorded deliveries are subtracted
exactly once. Do not enter only remaining inventory.

## Priority and allocation policy

- Lab members and matching dated Today's Requests orders are never recommended for
  cancellation. Their linked group is protected too.
- Groups containing an already fulfilled member are committed. An impossible
  protected/committed group produces an unresolved shortage and manual-review
  result, rather than overriding the protection.
- Among cancellable groups, people not yet served during the collection week's
  Monday-to-Sunday period are favored, followed by donor-specific requests, then
  the existing low-yield priority-sheet comparator: fulfillment rate, its existing
  volume/cancellation tie-breakers, request date, and order number. A linked group
  inherits its highest-priority member's ranking.
- Missed-pickup stars are a late tie-breaker: after weekly access, donor specificity,
  and fulfillment-history comparisons, fewer stars are favored before request
  date and order number. More stars do not automatically blacklist or cancel an
  order, override a stronger priority, or remove lab/Today's Requests protection
  or an already fulfilled linked commitment. A pending order's reason states its
  missed-pickup count when nonzero, without claiming it was the decisive factor.
  Counts are visible in the review, Who to Cancel, and Select Other Orders tables.
  New donor workbooks save counts in their input snapshot; older snapshots read
  the stars from the existing visible Missed Pickups columns, without visiting
  Google Sheets. Missing stars in an older workbook are treated as zero; regenerate
  the morning workbook if those data were absent or have changed.
- Before removing a group, the planner favors preserving a first weekly allocation
  for each requester over additional allocations to the same requester. It then
  removes the lowest-priority eligible group contributing to a shortage.
  Another pending order counts as alternative weekly coverage only if every pending
  cell type in its linked group has a known yield and the remaining active demand
  for each of those types fits the available supply after the proposed removal.
  Unknown-yield/HOLD groups and unresolved shortages do not justify sacrificing
  a currently fillable request. Confirmed deliveries and reviewed historical weekly
  fulfillment still count as already served. For example, Kara's unmeasured monocyte
  request no longer lowers the weekly-access importance of her PBMC request.
  Removed groups are reconsidered if they can fit after other cancellations.
- By default, kept groups reserve minimum quantities; if minimum is zero, maximum
  is used instead. Surplus is allocated toward maximums in priority order. Uncheck
  this option to reserve maximums throughout.
- This is a priority-first heuristic, not an exact global count optimizer. Strict
  priority can keep one large request instead of several smaller lower-priority
  requests. Weekly access and zero waste cannot be guaranteed with insufficient
  yields, missing history, or unknown future isolation yields. The report makes
  unmet weekly access visible.

## Saved data and limits

Confirmed outcomes and reviewed grouping/flags are saved under
`<parent of selected donor folder>/CancellationDecisionState/`, separately for each
donor/collection date. Confirmed positive distributions from other local records in
the same week contribute to the weekly flag, alongside fulfillment saved in the
morning workbook and confirmed PBMC outcomes. Recommendations themselves do not
count as fulfilled. Exported recommendations are written inside the selected donor
folder. Keep the sibling state folder to retain prior manual outcomes across donors.

Folders created before this snapshot format was added cannot safely supply comments
and explicit protection flags from the old visible sheets alone. Regenerate them
once with the updated program. If requests, quantities, comments, lab membership,
or Today's Requests protections change after the morning run, regenerate the donor
workbook: the planner intentionally uses the saved snapshot rather than live changes.
Manually editing the visible priority sheets does not edit the underlying saved
planner inputs; correct the source/preview and regenerate instead.

Use the same output directory across sessions and preserve this folder during app
updates. State is local to that directory, not automatically synchronized between
computers. Coordinate one active editor if sharing/copying it. Changing donor/date
starts a distinct ledger; recorded request quantity mismatches stop loading and
require reconciliation instead of silently reusing stale quantities.

Name matching for the existing Google Sheets and fulfillment services retains the
program's existing normalization; manually verify ambiguous names. Saved local
outcomes and planner groups use the complete normalized name. The current model
has one complete outcome per requester/order/cell row; it does not represent
partially completed requests, donor substitution, or splitting one request across
multiple donor inventories.
