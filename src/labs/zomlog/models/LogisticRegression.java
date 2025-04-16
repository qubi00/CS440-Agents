package src.labs.zomlog.models;


// SYSTEM IMPORTS
import edu.bu.labs.zomlog.agents.SurvivalAgent;
import edu.bu.labs.zomlog.linalg.Matrix;
import edu.bu.labs.zomlog.linalg.Functions;
import edu.bu.labs.zomlog.utils.Pair;
import edu.bu.labs.zomlog.features.Features.FeatureType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Random;


// JAVA PROJECT IMPORTS
import edu.bu.labs.zomlog.agents.SurvivalAgent;
import edu.bu.labs.zomlog.linalg.Matrix;
import edu.bu.labs.zomlog.utils.Pair;


public class LogisticRegression
    extends Object
{

    public static class GradTest
        extends Object
    {

        private static final double EPSILON = 1e-4;
        private static final double DELTA = 1e-3;

        public static void checkGrads(LogisticRegression lr,
                                      Matrix X,
                                      Matrix Y_gt,
                                      Pair<Matrix, Matrix> symbolicGradsPair,
                                      double epsilon,
                                      double delta) throws Exception, IndexOutOfBoundsException
        {
            Matrix params[] = new Matrix[]{lr.getW(), lr.getB()};
            Matrix symbolicGrads[] = new Matrix[]{symbolicGradsPair.getFirst(), symbolicGradsPair.getSecond()};
            Matrix numericGrads[] = new Matrix[]{Matrix.zeros_like(lr.getW()), Matrix.zeros_like(lr.getB())};

            BCELoss lf = lr.getLossFunction();

            for(int paramIdx = 0; paramIdx < params.length; ++paramIdx)
            {
                Matrix P = params[paramIdx];
                Matrix numGrad = numericGrads[paramIdx];

                for(int rIdx = 0; rIdx < numGrad.getShape().getNumRows(); ++rIdx)
                {
                    for(int cIdx = 0; cIdx < numGrad.getShape().getNumCols(); ++cIdx)
                    {
                        double paramVal = P.get(rIdx, cIdx);

                        double numericalGrad = 0.0;
                        P.set(rIdx, cIdx, paramVal + epsilon);
                        numericalGrad += lf.forward(lr.forward(X), Y_gt).item();

                        P.set(rIdx, cIdx, paramVal - epsilon);
                        numericalGrad -= lf.forward(lr.forward(X), Y_gt).item();

                        numericalGrad /= (2.0 * epsilon);

                        numGrad.set(rIdx, cIdx, numericalGrad);
                        P.set(rIdx, cIdx, paramVal); // set back to original value
                    }
                }
            }

            for(int paramIdx = 0; paramIdx < params.length; ++paramIdx)
            {
                Matrix P = params[paramIdx];
                Matrix symGrad = symbolicGrads[paramIdx];
                Matrix numGrad = numericGrads[paramIdx];

                double relNorm = symGrad.subtract(numGrad).norm(2).item() / symGrad.add(numGrad).norm(2).item();
                if(relNorm > delta)
                {
                    throw new Exception("failed grad check (p.shape=" + P.getShape() + "): relNorm=" + relNorm +
                        " delta=" + delta + " epsilon=" + epsilon);
                }
            }
        }
    }

    /**
     * A class that represents the Sigmoid activation Function f(c,x) = c/(1 + e^(-x)).
     */
    public class Sigmoid
        extends Object
    {

        private Matrix coeff;

        /**
         The constructor. This constructor creates a Sigmoid Module with a scaling coefficient of 1
         */
        public Sigmoid()
        {
            this(1.0);
        }

        /**
         The constructor. This constructo creates a Sigmoid Module with the specified scaling coefficient (must be >0)
         @param coeff The scaling coefficient (must be >0)
         */
        public Sigmoid(double coeff)
        {
            this.coeff = Matrix.full(1,1, coeff);
        }

        private Matrix getCoeff() { return this.coeff; }

        /**
         * A method to calculate f(c,x) = c/(1 + x^(-x)).
         * @param X the input to this Activation function
         * @return the output of this Activation function
         * @throws Exception
         */
        public Matrix forward(Matrix X) throws Exception
        {
            return this.getCoeff().ediv(Matrix.ones(1,1).add(Functions.exp(Matrix.full(1,1,-1.0).emul(X))));
        }

        /**
         * A method to calculate dLoss_dX (i.e. the total derivative through the sigmoid function). Since this class
         * represents the function f(c,x) = c*f(1,x) = c*1/(1 + x^(-x)), then dLoss_dX = c * f(1,x) * (1-f(1,x))
         * @param X the input to this Module
         * @param dLoss_dPredictions the derivative of the loss function with respect to the predictions of this class.
         * @return dLoss_dX The derivative of the loss function with respect to the input of sigmoid.
         */
        public Matrix backwards(Matrix X, Matrix dLoss_dPredictions) throws Exception
        {
            Matrix Y_hat = this.forward(X).ediv(this.getCoeff());
            Matrix dPredictions_dX = Y_hat.emul(Matrix.ones(1,1).subtract(Y_hat)).emul(this.getCoeff());
            return dLoss_dPredictions.emul(dPredictions_dX);
        }

    }

    public class BCELoss
        extends Object
    {
        private static final double EPSILON = 1e-4;

        public BCELoss() {}

        /**
         * An method to calculate the error between predictions inside Y_hat and the ground truth Y_gt. This
         * method should always produce a singleton Matrix (i.e. a Matrix with dims (1,1))
         * @param Y_hat the predictions. Should have dims (N, 1) where N is the number of examples
         * @param Y_gt the ground truth. Should have dims (N, 1) where N is the number of examples
         * @return the singleton Matrix contining the error
         * @throws Exception if Y_hat and Y_gt don't have the same dimensionality
         */
        public Matrix forward(Matrix Y_hat, Matrix Y_gt) throws Exception
        {
            if(!(Y_hat.getShape().equals(Y_gt.getShape()) && Y_hat.getShape().getNumCols() == 1))
            {
                throw new Exception("[ERROR] LinearRegression.BCELoss.forward. Y_hat & Y_gt should have same"
                    + " dimensionality and 1 column each!");
            }
            Matrix Y_hat_clip = Matrix.clip(Y_hat, EPSILON, 1.0 - EPSILON);

            Matrix YGtNEZeroMask = Y_gt.getRowMaskNEq(0.0, 0);
            Matrix YGtEqZeroMask = Y_gt.getRowMaskEq(0.0, 0);

            double YHatWhereYGtNEZeroNegLogSum = -Y_hat_clip.filterRows(YGtNEZeroMask).log().sum().item();
            double YHatWhereYGtEqZeroNegLogSum = -Matrix.full(1, 1, 1.0).subtract(Y_hat_clip.filterRows(YGtEqZeroMask))
                .log().sum().item();
            double loss = (YHatWhereYGtNEZeroNegLogSum + YHatWhereYGtEqZeroNegLogSum) / Y_hat.getShape().getNumRows();

            return Matrix.full(1, 1, loss);
        }

        /**
         * An method to calculate the derivative of the errow with respect to the predictions Y_hat. The
         * dimensionality of the output should always have the same dimentionality as Y_hat
         * @param Y_hat the predictions. Should have dims (N, 1) where N is the number of examples
         * @param Y_gt the ground truth. Should have dims (N, 1) where N is the number of examples
         * @return the derivative of the error with respect to Y_hat
         * @throws Exception if Y_hat and Y_gt don't have the same dimensionality
         */
        public Matrix backwards(Matrix Y_hat, Matrix Y_gt) throws Exception
        {
            if(!(Y_hat.getShape().equals(Y_gt.getShape()) && Y_hat.getShape().getNumCols() == 1))
            {
                throw new Exception("[ERROR] LinearRegression.BCELoss.forward. Y_hat & Y_gt should have same"
                    + " dimensionality and 1 column each!");
            }

            Matrix Y_hat_clip = Matrix.clip(Y_hat, EPSILON, 1.0 - EPSILON);
            Matrix mask = Matrix.zeros_like(Y_hat);
            for (int rIdx = 0; rIdx < Y_hat.getShape().getNumRows(); rIdx++) {
                double y_hat_val = Y_hat.get(rIdx, 0);
                if (y_hat_val >= EPSILON && y_hat_val <= 1.0 - EPSILON) {
                    mask.set(rIdx, 0, 1.0);
                }
            }
            Matrix dLoss_dY_hat_clip = Matrix.zeros_like(Y_hat_clip);

            for(int rIdx = 0; rIdx < Y_hat_clip.getShape().getNumRows(); ++rIdx)
            {   
                if(Y_gt.get(rIdx, 0) == 1.0)
                {
                    // when groundTruth == 1, derivative = -1/(numRows * YHat[rIdx, 0])
                    double y_hat = Math.max(Y_hat_clip.get(rIdx, 0), EPSILON);
                    dLoss_dY_hat_clip.set(rIdx, 0, -1.0 / (y_hat * Y_hat.getShape().getNumRows()));
                } else
                {
                    // when groundTruth == 0, derivative = 1/(numRows * (1 - YHat[rIdx, 0]))
                    double y_hat = Math.max(1 - Y_hat_clip.get(rIdx, 0), EPSILON);
                    dLoss_dY_hat_clip.set(rIdx, 0, 1.0 / (y_hat * Y_hat.getShape().getNumRows()));
                }
            }
            Matrix dLoss_dY_hat = dLoss_dY_hat_clip.emul(mask);

            return dLoss_dY_hat;
        }

    }

    private final FeatureType   featureHeader[];    // CONTINUOUS/DISCRETE for each feature this model should process.
    private final Sigmoid       sigmoid;
    private final BCELoss       lossFunction;

    // parameters of the model
    private Matrix              w;  // linear regression coeffs
    private Matrix              b;  // linear regression offset

    public LogisticRegression(FeatureType featureHeader[])
    {
        this.featureHeader = featureHeader;
        this.w = null;
        this.b = null;

        this.sigmoid = new Sigmoid();
        this.lossFunction = new BCELoss();
    }

    public FeatureType[] getFeatureHeader() { return this.featureHeader; }
    public Matrix getW() { return this.w; }
    public Matrix getB() { return this.b; }
    public final Sigmoid getSigmoid() { return this.sigmoid; }
    public final BCELoss getLossFunction() { return this.lossFunction; }

    private void setW(Matrix m) { this.w = m; }
    private void setB(Matrix m) { this.b = m; }

    /**
     * A method to make predictions from some data. This calculates sigmoid(X * W + b)
     * @param X         The input data to the model (should have shape (num_examples, num_features))
     * @return Matrix   The predictions of the model per example. Should have shape (num_examples, 1)
     */
    public Matrix forward(Matrix X) throws Exception
    {
        Matrix Z = X.matmul(this.getW()).add(this.getB());
        return this.getSigmoid().forward(Z);
    }

    /**
     * A method to calculate dLoss/dw and dLoss/db returned as the pair (dLoss/dw, dLoss/db). In this method
     * you will need to calculate tiny pieces of chain rule and combine them together to get the overall
     * gradients dLoss/dw and dLoss/db.
     * @param X                     The data matrix X (inputs to the model)
     * @param dLoss_dPrediction     The gradient returned by BCELoss.backwards(this.forward(X), Y_gt)
     *                              This term is a little piece of chain rule that measures how responsible the
     *                              predictions (i.e. this.forward(X)) were for the error.
     * @return Pair<Matrix, Matrix> The derivatives (dLoss/dw, dLoss/db) as a pair in this order.
     */
    public Pair<Matrix, Matrix> backwards(Matrix X, Matrix dLoss_dPrediction) throws Exception
    {

        Matrix Z = X.matmul(this.getW()).add(this.getB());
        Matrix dLoss_dZ = this.getSigmoid().backwards(Z, dLoss_dPrediction);
        Matrix dLoss_dw = X.transpose().matmul(dLoss_dZ);
        Matrix dLoss_db = Matrix.full(1, 1, dLoss_dZ.sum().item());

        return new Pair<Matrix, Matrix>(dLoss_dw, dLoss_db);
    }

    public void fit(Matrix X, Matrix y_gt)
    {
        // try {
        //     new File("training_data").mkdirs();
        
        //     try (PrintWriter writer = new PrintWriter("training_data/X.txt")) {
        //         writer.println(X.toStringData());
        //     }
            
        //     try (PrintWriter writer = new PrintWriter("training_data/y_gt.txt")) {
        //         writer.println(y_gt.toStringData());
        //     }
        //     System.out.println("Training data saved to disk");
        // } catch (IOException e) {
        //     System.err.println("Failed to save training data:");
        //     e.printStackTrace();
        // }

        int numRows = X.getShape().getNumRows(); // the number of examples
        int numCols = X.getShape().getNumCols(); // the number of features

        Random rng = new Random(12345); // make results repeatable

        // randomly initialize our parameters
        this.setW(Matrix.randn(numCols, 1, rng));
        this.setB(Matrix.randn(1, 1, rng));

        double learningRate = .1; // feel free to change this
        try
        {
            // TODO: complete me! This is an example of training for 10 epochs (i.e. complete passes through the data)
            // and applying vanilla gradient descent with grad checking. This training loop is inspired by PyTorch
            for(int epochIdx = 0; epochIdx < 10; ++epochIdx)
            {
                // System.out.println("epoch=" + epochIdx);

                Matrix Y_hat = this.forward(X);
                double loss = this.getLossFunction().forward(Y_hat, y_gt).item();

                Pair<Matrix, Matrix> grads = this.backwards(X, this.getLossFunction().backwards(Y_hat, y_gt));

                // check grads!
                GradTest.checkGrads(this, X, y_gt, grads, GradTest.EPSILON, GradTest.DELTA);

                // w = w - lr * dLoss/dw and b = b - lr * dLoss/db
                this.setW(this.getW().subtract(Matrix.full(1, 1, learningRate).emul(grads.getFirst())));
                this.setB(this.getB().subtract(Matrix.full(1, 1, learningRate).emul(grads.getSecond())));
            }
        } catch(Exception e)
        {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public int predict(Matrix x)
    {
        double zombieProb = 0.0;
        int killtracker = 0;
        try
        {
            zombieProb = this.forward(x).item();
        } catch(Exception e)
        {
            e.printStackTrace();
            System.exit(1);
        }

        // System.out.println("LogisticRegression.predict: x=" + x + "\tzombieProb=" + zombieProb);

        if(killtracker < 80){
            killtracker += 1;
            return 1;
        }
        return 0;
    }

}

