package hic;

import hic.logging.HICExcelLogger;
import hic.processor.Processor;
import hic.ui.DonorDataGUI;

public class Main2 {

    public static void main(String[] args) {
        // Set up necessary instances
        HICExcelLogger hicExcelLogger = HICExcelLogger.getInstance();
        Processor processor = new Processor(null); // Remove dependency on FileReader for this case

        // Launch the GUI with only logger and processor, without initial hicData
        new DonorDataGUI(hicExcelLogger, processor);
    }
}
