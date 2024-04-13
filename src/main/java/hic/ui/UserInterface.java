package hic.ui;//package ui;
//
//import edu.upenn.cit594.datamanagement.FileReader;
//import edu.upenn.cit594.processor.AverageMarketValueProcessor;
//import edu.upenn.cit594.processor.AverageTotalLivableAreaProcessor;
//import edu.upenn.cit594.processor.Processor;
//import edu.upenn.cit594.util.CovidData;
//import edu.upenn.cit594.util.PopulationData;
//
//import java.util.List;
//import java.util.Objects;
//import java.util.Scanner;
//
//public class UserInterface {
//
//    private FileReader fileReader;
//    private Processor processor;
//
//    public UserInterface() {
//        fileReader = FileReader.getInstance();
//        processor = new Processor(fileReader);
//    }
//
//    //TODO: Adi
//    public void mainMenu(String covidArg, String propertyArg, String populationArg, String logArg) {
//
//        Scanner scanner = new Scanner(System.in);
//        boolean quit = false;
//
//        String keyword = null;
//        if (covidArg.contains(".csv")) {
//            keyword = "covidData";
//        }
//
//        while (!quit) {
//            printMainMenu();
//            int choice = scanner.nextInt();
//
//            switch (choice) {
//                case 0:
//                    //exitProgram();
//                    quit = true;
//                    break;
//                case 1:
//                    showAvailableActions();
//                    break;
//                case 2:
//                    processor.getTotalPopulationForZip(populationArg);
//                    break;
//                case 3:
//                    getVaccinations(covidArg, populationArg, keyword);
//                    break;
//                case 4:
//                    showAvgMarketValZip(propertyArg);
//                    break;
//                case 5:
//                    showAvgLivableAreaZip(propertyArg);
//                    break;
//                case 6:
//                    showMarketValProperties(propertyArg, populationArg);
//                    break;
//                case 7:
//                    customAction();
//                    break;
//                default:
//                    System.out.println("Invalid selection. Please try again.\n");
//            }
//        }
//
//
//
//    }
//
//    //TODO: All of these methods are already created in the processor package, but they need to be scanner friendly
//
//
//    private void printMainMenu() {
//
//        System.out.println("\nPlease choose from the list: ");
//        System.out.println();
//        System.out.println("0. Exit the program");
//        System.out.println("1. Show available actions");
//        System.out.println("2. Show the total population for all ZIP Codes");
//        System.out.println("3. Show the total vaccinations per capita for each ZIP Code for a specified date");
//        System.out.println("4. Show the average market value for properties in a specified ZIP Code");
//        System.out.println("5. Show the average total livable area for properties in a specified ZIP Code");
//        System.out.println("6. Show the total market value of properties, per capita, for a specified ZIP Code");
//        System.out.println("7. Custom feature (TODO)");
//    }
//
//    //TODO: Adi
//    private void exitProgram() {
//    }
//
//    //TODO: Adi
//    private void showAvailableActions() {
//    }
//
//    private void getTotalPopulation() {
//
//    }
//
//    //TODO: Adi
//    private void getVaccinations(String covidArg, String populationArg, String keyword) {
//
//        List<CovidData> covidDataList = fileReader.parseFile(covidArg, keyword);
//
//        List<PopulationData> populationDataList = fileReader.parseFile(populationArg, "populationData");
//
//        Scanner scanner = new Scanner(System.in);
//
//        boolean partial = false;
//
//        System.out.println("Please specify whether you want to inspect 'partial' or 'full' vaccinations: ");
//
//        String vaxChoice = scanner.next();
//
//        if (Objects.equals(vaxChoice, "partial")) {
//            partial = true;
//        } else if (Objects.equals(vaxChoice, "full")) {
//            partial = false;
//        } else {
//            System.out.println("Please pick a valid choice.");
//            return;
//        }
//
//        System.out.println("Please enter a date you want to inspect in the format YYYY-MM-DD: ");
//
//        String dateChoice = scanner.next();
//
//        processor.getVaccinationsPerCapita(covidDataList, populationDataList, dateChoice, partial);
//
//    }
//
//    //TODO: Adi
//    private void showAvgMarketValZip(String propertyArg) {
//
//        Scanner scanner = new Scanner(System.in);
//
//        System.out.println("Please enter a 5-digit ZIP code to investigate:");
//
//        String zipCode = scanner.next();
//
//        AverageMarketValueProcessor averageMarketValueProcessor = new AverageMarketValueProcessor(fileReader);
//
//        averageMarketValueProcessor.processAverage(propertyArg, zipCode);
//    }
//
//    //TODO: Adi
//    private void showAvgLivableAreaZip(String propertyArg) {
//
//        Scanner scanner = new Scanner(System.in);
//
//        System.out.println("Please enter a 5-digit ZIP code to investigate:");
//
//        String zipCode = scanner.next();
//
//        AverageTotalLivableAreaProcessor averageTotalLivableAreaProcessor = new AverageTotalLivableAreaProcessor(fileReader);
//
//        averageTotalLivableAreaProcessor.processAverage(propertyArg, zipCode);
//    }
//
//    //TODO: Adi
//    private void showMarketValProperties(String propertyArg, String populationArg) {
//
//        Scanner scanner = new Scanner(System.in);
//
//        System.out.println("Please enter a 5-digit ZIP code to investigate:");
//
//        String zipCode = scanner.next();
//
//        processor.getTotalMarketValuePerCapita(propertyArg, populationArg, zipCode);
//
//    }
//
//    //TODO: Adi
//    private void customAction() {
//    }
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//
//}
