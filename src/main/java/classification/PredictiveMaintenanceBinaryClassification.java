package classification;

import org.datavec.api.records.reader.RecordReader;
import org.datavec.api.records.reader.SequenceRecordReader;
import org.datavec.api.records.reader.impl.collection.CollectionSequenceRecordReader;
import org.datavec.api.records.reader.impl.csv.CSVRecordReader;
import org.datavec.api.split.FileSplit;
import org.datavec.api.transform.TransformProcess;
import org.datavec.api.transform.schema.Schema;
import org.datavec.api.transform.sequence.comparator.NumericalColumnComparator;
import org.datavec.api.writable.Writable;
import org.datavec.local.transforms.LocalTransformExecutor;
import org.deeplearning4j.datasets.datavec.SequenceRecordReaderDataSetIterator;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.common.io.ClassPathResource;
import org.nd4j.evaluation.classification.Evaluation;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerMinMaxScaler;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.schedule.CycleSchedule;
import org.nd4j.linalg.schedule.ScheduleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Predictive maintenance techniques are designed to help determine the condition of in-service equipment in order to
// estimate when maintenance should be performed. Predictive maintenance can be modeled in several ways,
// 1. Predict the Remaining Useful Life (RUL), or Time to Failure (TTF)
// 2. Predict if the asset will fail by given a certain time frame
// 3. Predict critical level of the asset by give a certain time frame

// This example we will look at the 2nd modeling strategy which is to predict weather the asset is going to fail.
// The label consist of 0 and 1. 0 means the assets is working fine and 1 means it require maintenance.

public class PredictiveMaintenanceBinaryClassification {
    static int sequenceLength = 30;
    static int batchSize = 200;
    static int numOfLabel = 2;
    static int labelIndex = 20;
    static int epochs = 75;

    static Logger log = LoggerFactory.getLogger(PredictiveMaintenanceBinaryClassification.class);

    public static void main(String[] args) throws IOException, InterruptedException {
        File trainDataset = new ClassPathResource("/datasets/predictivemaintenance/train.csv").getFile();
        File testDataset = new ClassPathResource("/datasets/predictivemaintenance/test.csv").getFile();

        Schema inputDataSchema = new Schema.Builder()
                .addColumnsInteger("id", "cycle")
                .addColumnsDouble("setting1", "setting2", "setting3",
                        "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "s10",
                        "s11", "s12", "s13", "s14", "s15", "s16", "s17", "s18", "s19", "s20", "s21", "cycle_norm"
                )
                .addColumnsInteger("RUL", "label1", "label2")
                .build();

        TransformProcess tp = new TransformProcess.Builder(inputDataSchema)
                .removeColumns("setting3", "s1", "s5", "s10", "s16", "s18", "s19", "RUL", "label2")
                .convertToSequence(Collections.singletonList("id"), new NumericalColumnComparator("cycle"))
                .build();

        int numLinesToSkip = 1;
        char delimiter = ',';
        RecordReader trainCSVRR = new CSVRecordReader(numLinesToSkip, delimiter);
        RecordReader testCSVRR = new CSVRecordReader(numLinesToSkip, delimiter);

        trainCSVRR.initialize(new FileSplit(trainDataset));
        testCSVRR.initialize(new FileSplit(testDataset));

        List<List<Writable>> trainWritable = new ArrayList<>();
        while (trainCSVRR.hasNext()) {
            trainWritable.add(trainCSVRR.next());
        }

        List<List<Writable>> testWritable = new ArrayList<>();
        while (testCSVRR.hasNext()) {
            testWritable.add(testCSVRR.next());
        }

        List<List<List<Writable>>> trainWritableSequence = LocalTransformExecutor.executeToSequence(trainWritable, tp);
        List<List<List<Writable>>> testWritableSequence = LocalTransformExecutor.executeToSequence(testWritable, tp);

        // Iterate over trainWritableSequence.
        // For example id1 have 192 rows and sequenceLength is equal to 30
        // Here separate the 192 rows into small fixed length sequence with the size of predefined sequence length
        // 0 30 -> from row 0 to row 30
        // 1 31 -> from row 1 to row 31
        // 2 32 -> from row 2 to row 32
        // ...
        // 111 191 -> from row 111 to 191
        List<List<List<Writable>>> trainWritableSequence2 = new ArrayList<>();
        for (List<List<Writable>> engineSample : trainWritableSequence) {
            for (int j = 0; j < (engineSample.size() - sequenceLength); j++) {
                trainWritableSequence2.add(engineSample.subList(j, j + sequenceLength));
            }
        }

        List<List<List<Writable>>> testWritableSequence2 = new ArrayList<>();
        for (List<List<Writable>> engineSample : testWritableSequence) {
            for (int j = 0; j < (engineSample.size() - sequenceLength); j++) {
                testWritableSequence2.add(engineSample.subList(j, j + sequenceLength));
            }
        }

        SequenceRecordReader trainSequenceRecordReader = new CollectionSequenceRecordReader(trainWritableSequence2);
        SequenceRecordReader testSequenceRecordReader = new CollectionSequenceRecordReader(testWritableSequence2);

        SequenceRecordReaderDataSetIterator trainIter = new SequenceRecordReaderDataSetIterator(trainSequenceRecordReader, batchSize, numOfLabel, labelIndex);
        SequenceRecordReaderDataSetIterator testIter = new SequenceRecordReaderDataSetIterator(testSequenceRecordReader, batchSize, numOfLabel, labelIndex);

        DataNormalization scaler = new NormalizerMinMaxScaler();
        scaler.fit(trainIter);
        trainIter.setPreProcessor(scaler);
        testIter.setPreProcessor(scaler);


        // Scheduler
//        Map<Integer,Double> learningRateSchedule = new HashMap<>();
//        learningRateSchedule.put(0,0.01);
//        learningRateSchedule.put(20,0.001); // learning rate reduced to 0.001 after 20 epochs
//        learningRateSchedule.put(40,0.0001); // learning rate reduced to 0.0001 after 40 epochs
//        MapSchedule mapScheduler = new MapSchedule(ScheduleType.EPOCH, learningRateSchedule);

        CycleSchedule cycleSchedule = new CycleSchedule(ScheduleType.EPOCH, 0.00001, 0.01, epochs, (int) Math.round(epochs * 0.1), 0.1);

        int numInput = trainIter.inputColumns();
        MultiLayerConfiguration config = new NeuralNetConfiguration.Builder()
                .seed(123)
                .l2(0.00001)
                .weightInit(WeightInit.XAVIER)
                .updater(new Adam(cycleSchedule))
                .list()
                .layer(new LSTM.Builder()
                        .name("lstm")
                        .nIn(numInput)
                        .nOut(100)
                        .activation(Activation.TANH)
                        .dropOut(0.9)
                        .build())
                .layer(new LSTM.Builder()
                        .name("lstm2")
                        .nOut(50)
                        .activation(Activation.TANH)
                        .dropOut(0.9)
                        .build())
                .layer(new RnnOutputLayer.Builder()
                        .name("output")
                        .nOut(numOfLabel)
                        .lossFunction(LossFunctions.LossFunction.MCXENT)
                        .build())
                .build();

        MultiLayerNetwork model = new MultiLayerNetwork(config);
        model.init();
        model.setListeners(new ScoreIterationListener(10));

        for (int i = 0; i < epochs; i++) {
            log.info("EPOCH: " + i);
            model.fit(trainIter);

            Evaluation eval = model.evaluate(testIter);
            log.info(eval.stats());
        }
    }
}