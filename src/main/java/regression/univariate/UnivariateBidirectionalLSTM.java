package regression.univariate;

import org.deeplearning4j.core.storage.StatsStorage;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.conf.layers.recurrent.Bidirectional;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.ui.api.UIServer;
import org.deeplearning4j.ui.model.stats.StatsListener;
import org.deeplearning4j.ui.model.storage.InMemoryStatsStorage;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.RmsProp;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import static regression.univariate.Helper.expandDimsHelper;

/**
 * Simple Time Series Sequence Prediction using Bidirectional LSTM
 *
 * In this example, we will show you the method in performing time series prediction using synthetic time series data.
 * We can perform the prediction in following steps:
 *  1. Initialize the data
 *      Here, we will generate a sequence of synthetic time series data, i.e. [10, 20, 30, 40, 50, 60, 70, 80, 90]
 *
 *  2. Preprocess the data into lagged features and labels.
 *      In time series analysis, we need to provide a lagged feature sequence and their respective label.
 *      For example, if the lagged feature is [[10,20,30]] and the label will be [40].
 *
 *  3. Setup the Bidirectional LSTM configuration
 *
 *  4. Setup UI server for training
 *
 *  5. Train the model
 *
 *  6. Perform time series predictions
 *
 * This example is inspired by Jason Brownlee from Machine Learning Mastery
 * Src: https://machinelearningmastery.com/how-to-develop-lstm-models-for-time-series-forecasting/
 *
 */

public class UnivariateBidirectionalLSTM {

    private static double learningRate = 0.001;

    public static void main(String[] args) {

        // Step 1: Initialize the data
        double[] sequenceData = new double[]{10, 20, 30, 40, 50, 60, 70, 80, 90};

        // Step 2: Preprocess the data into lagged features and labels.
        TimeSeriesUnivariateData data = new TimeSeriesUnivariateData(sequenceData,3);
        INDArray feature = data.getFeatureMatrix();
        INDArray label = data.getLabels();

        // Step 3: Setup the Bidirectional LSTM configuration
        int sequenceLength = data.getSequenceLength();
        MultiLayerConfiguration config = new NeuralNetConfiguration.Builder()
                .seed(123)
                .updater(new RmsProp(learningRate))
                .weightInit(WeightInit.XAVIER)
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .miniBatch(false)
                .list()
                .layer(0, new Bidirectional(new LSTM.Builder()
                        .nIn(sequenceLength)
                        .nOut(50)
                        .activation(Activation.TANH)
                        .build()))
                .layer(1, new RnnOutputLayer.Builder()
                        .nIn(100)
                        .nOut(1)
                        .lossFunction(LossFunctions.LossFunction.MSE)
                        .activation(Activation.IDENTITY)
                        .build())
                .build();

        MultiLayerNetwork network = new MultiLayerNetwork(config);
        network.init();

        // Step 4: Setup UI server for training
        UIServer server = UIServer.getInstance();
        StatsStorage storage = new InMemoryStatsStorage();
        server.attach(storage);
        network.setListeners(new StatsListener(storage, 10));

        // Step 5: Train the model
        for (int i = 0; i < 10000; i++) {
            network.fit(feature, label);
        }

        // Step 6: Perform time series predictions
        // Note: We need to reshape from [samples, timesteps] into [samples, timesteps, num of features], here the number of feature is 1
        INDArray testInput1 = expandDimsHelper(Nd4j.create(new double[]{10, 20, 30}));
        System.out.println(network.output(testInput1));

        INDArray testInput2 = expandDimsHelper(Nd4j.create(new double[]{20, 30, 40}));
        System.out.println(network.output(testInput2));

        INDArray testInput3 = expandDimsHelper(Nd4j.create(new double[]{50, 60, 70}));
        System.out.println(network.output(testInput3));
    }
}