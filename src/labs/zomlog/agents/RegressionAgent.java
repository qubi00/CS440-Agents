package src.labs.zomlog.agents;


// SYSTEM IMPORTS
import edu.bu.labs.zomlog.agents.SurvivalAgent;
import edu.bu.labs.zomlog.linalg.Matrix;
import edu.bu.labs.zomlog.utils.Pair;
import edu.bu.labs.zomlog.features.Features.FeatureType;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


// JAVA PROJECT IMPORTS
import src.labs.zomlog.models.LogisticRegression;



public class RegressionAgent
    extends SurvivalAgent
{

    public static final FeatureType[] FEATURE_HEADER = {FeatureType.CONTINUOUS,
                                                        FeatureType.CONTINUOUS,
                                                        FeatureType.DISCRETE,
                                                        FeatureType.DISCRETE};

    private LogisticRegression model;

    public RegressionAgent(int playerNum, String[] args)
    {
        super(playerNum, args);
        this.model = new LogisticRegression(FEATURE_HEADER);
    }

    public LogisticRegression getModel() { return this.model; }

    @Override
    public void train(Matrix X, Matrix y_gt)
    {
        System.out.println(X.getShape() + " " + y_gt.getShape());
        this.getModel().fit(X, y_gt);
    }

    @Override
    public int predict(Matrix featureRowVector)
    {
        return this.getModel().predict(featureRowVector);
    }

}
