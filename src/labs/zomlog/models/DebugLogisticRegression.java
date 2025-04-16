package src.labs.zomlog.models;

import edu.bu.labs.zomlog.features.Features.FeatureType;
import edu.bu.labs.zomlog.linalg.Matrix;
import java.io.*;

public class DebugLogisticRegression {
    public static void main(String[] args) {
        try {
            // Load features (X)
            Matrix X = loadMatrixFromFile("training_data/X.txt");
            
            // Load labels (y_gt)
            Matrix y_gt = loadMatrixFromFile("training_data/y_gt.txt");
            
            System.out.println("Loaded training data:");
            System.out.println("X shape: " + X.getShape().getNumRows() + "x" + X.getShape().getNumCols());
            System.out.println("y_gt shape: " + y_gt.getShape().getNumRows() + "x" + y_gt.getShape().getNumCols());
            
            // Initialize model with actual feature types
            FeatureType[] featureTypes = new FeatureType[]{
                FeatureType.CONTINUOUS, 
                FeatureType.CONTINUOUS,
                FeatureType.DISCRETE,
                FeatureType.DISCRETE
            };
            LogisticRegression model = new LogisticRegression(featureTypes);
            
            // Train the model
            model.fit(X, y_gt);
            
            // Test prediction
            Matrix testSample = X.getSlice(0, 1, 0, X.getShape().getNumCols());
            System.out.println("Test prediction: " + model.predict(testSample));
            
        } catch (Exception e) {
            System.err.println("Error during debugging:");
            e.printStackTrace();
        }
    }
    
    private static Matrix loadMatrixFromFile(String filename) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String matrixString = reader.readLine();
            
            // Parse shape and data
            String shapePart = matrixString.substring(0, matrixString.indexOf(';'));
            String dataPart = matrixString.substring(matrixString.indexOf(';') + 1);
            
            // Extract rows and columns from shape
            int numRows = Integer.parseInt(shapePart.substring(1, shapePart.indexOf('x')));
            int numCols = Integer.parseInt(shapePart.substring(shapePart.indexOf('x') + 1, shapePart.indexOf(']')));
            
            // Create matrix
            Matrix matrix = Matrix.zeros(numRows, numCols);
            
            // Populate data
            String[] dataValues = dataPart.split(",");
            int index = 0;
            for (int row = 0; row < numRows; row++) {
                for (int col = 0; col < numCols; col++) {
                    matrix.set(row, col, Double.parseDouble(dataValues[index]));
                    index++;
                }
            }
            
            return matrix;
        }
    }
}