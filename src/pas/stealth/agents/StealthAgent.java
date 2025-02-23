package src.pas.stealth.agents;


// SYSTEM IMPORTS
import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.environment.model.history.History.HistoryView;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.util.Direction;


import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
//
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.Set;


// JAVA PROJECT IMPORTS
import edu.bu.pas.stealth.agents.AStarAgent;                // the base class of your class
import edu.bu.pas.stealth.agents.AStarAgent.AgentPhase;     // INFILTRATE/EXFILTRATE enums for your state machine
import edu.bu.pas.stealth.agents.AStarAgent.ExtraParams;    // base class for creating your own params objects
import edu.bu.pas.stealth.graph.Vertex;                     // Vertex = coordinate
import edu.bu.pas.stealth.graph.Path;                       // see the documentation...a Path is a linked list



public class StealthAgent
    extends AStarAgent
{

    // Fields of this class
    // TODO: add your fields here! For instance, it might be a good idea to
    // know when you've killed the enemy townhall so you know when to escape!
    // TODO: implement the state machine for following a path once we calculate it
    //       this will for sure adding your own fields.
    private int enemyChebyshevSightLimit;
    private Path currentPath;
    private AgentPhase phase;
    

    public StealthAgent(int playerNum)
    {
        super(playerNum);

        this.enemyChebyshevSightLimit = -1; // invalid value....we won't know this until initialStep()
        phase = AgentPhase.INFILTRATE;
    }

    // TODO: add some getter methods for your fields! Thats the java way to do things!
    public final int getEnemyChebyshevSightLimit() { return this.enemyChebyshevSightLimit; }

    public void setEnemyChebyshevSightLimit(int i) { this.enemyChebyshevSightLimit = i; }


    ///////////////////////////////////////// Sepia methods to override ///////////////////////////////////

    /**
        TODO: if you add any fields to this class it might be a good idea to initialize them here
              if they need sepia information!
     */
    @Override
    public Map<Integer, Action> initialStep(StateView state,
                                            HistoryView history)
    {
        super.initialStep(state, history); // call AStarAgent's initialStep() to set helpful fields and stuff

        // now some fields are set for us b/c we called AStarAgent's initialStep()
        // let's calculate how far away enemy units can see us...this will be the same for all units (except the base)
        // which doesn't have a sight limit (nor does it care about seeing you)
        // iterate over the "other" (i.e. not the base) enemy units until we get a UnitView that is not null
        UnitView otherEnemyUnitView = null;
        Iterator<Integer> otherEnemyUnitIDsIt = this.getOtherEnemyUnitIDs().iterator();
        while(otherEnemyUnitIDsIt.hasNext() && otherEnemyUnitView == null)
        {
            otherEnemyUnitView = state.getUnit(otherEnemyUnitIDsIt.next());
        }

        if(otherEnemyUnitView == null)
        {
            System.err.println("[ERROR] StealthAgent.initialStep: could not find a non-null 'other' enemy UnitView??");
            System.exit(-1);
        }

        // lookup an attribute from the unit's "template" (which you can find in the map .xml files)
        // When I specify the unit's (i.e. "footman"'s) xml template, I will use the "range" attribute
        // as the enemy sight limit
        this.setEnemyChebyshevSightLimit(otherEnemyUnitView.getTemplateView().getRange());

        return null;
    }

    /**
        TODO: implement me! This is the method that will be called every turn of the game.
              This method is responsible for assigning actions to all units that you control
              (which should only be a single footman in this game)
     */
    @Override
    public Map<Integer, Action> middleStep(StateView state,
                                           HistoryView history)
    {
        Map<Integer, Action> actions = new HashMap<Integer, Action>();

        /**
            I would suggest implementing a state machine here to calculate a path when neccessary.
            For instance beginning with something like:

            if(this.shouldReplacePlan(state))
            {
                // recalculate the plan
            }

            then after this, worry about how you will follow this path by submitting sepia actions
            the trouble is that we don't want to move on from a point on the path until we reach it
            so be sure to take that into account in your design

            once you have this working I would worry about trying to detect when you kill the townhall
            so that you implement escaping
         */
        int unitId = this.getMyUnitID();

        if(this.shouldReplacePlan(state))
        {
            Vertex start = new Vertex(state.getUnit(unitId).getXPosition(), state.getUnit(unitId).getXPosition());
            Vertex goal = 
        }

        return actions;
    }

    ////////////////////////////////// End of Sepia methods to override //////////////////////////////////

    /////////////////////////////////// AStarAgent methods to override ///////////////////////////////////
    
    public boolean isValidMove(Vertex v, StateView state){
        return(v.getXCoordinate() >= 0 && v.getYCoordinate() >= 0 && 
        v.getXCoordinate() < state.getXExtent() && v.getYCoordinate() < state.getYExtent());
    }

    public Collection<Vertex> getNeighbors(Vertex v,
                                           StateView state,
                                           ExtraParams extraParams)
    {   
        Collection<Vertex> neighbors = new ArrayList<>();
        Vertex neighbor = null;
        for (Direction dir : Direction.values()){
            if(dir.equals(Direction.NORTH)){
                neighbor = new Vertex(v.getXCoordinate(), v.getYCoordinate() - 1);
            }else if(dir.equals(Direction.SOUTH)){
                neighbor = new Vertex(v.getXCoordinate(), v.getYCoordinate() + 1);

            }else if(dir.equals(Direction.EAST)){
                neighbor = new Vertex(v.getXCoordinate() + 1, v.getYCoordinate());
                
            }else if(dir.equals(Direction.WEST)){
                neighbor = new Vertex(v.getXCoordinate() - 1, v.getYCoordinate());
                
            }else if(dir.equals(Direction.NORTHEAST)){
                neighbor = new Vertex(v.getXCoordinate() + 1, v.getYCoordinate() - 1);
                
            }else if(dir.equals(Direction.NORTHWEST)){
                neighbor = new Vertex(v.getXCoordinate() - 1, v.getYCoordinate() - 1);
                
            }else if(dir.equals(Direction.SOUTHEAST)){
                neighbor = new Vertex(v.getXCoordinate() + 1, v.getYCoordinate() + 1);
                
            }else if(dir.equals(Direction.SOUTHWEST)){
                neighbor = new Vertex(v.getXCoordinate() - 1, v.getYCoordinate() + 1);
            }

            if(neighbor != null && isValidMove(neighbor, state) 
            && state.isResourceAt(neighbor.getXCoordinate(), neighbor.getYCoordinate())){
                neighbors.add(neighbor);
            }
        }
        return neighbors;
    }

    public Path aStarSearch(Vertex src,
                            Vertex dst,
                            StateView state,
                            ExtraParams extraParams)
    {
        PriorityQueue<Path> openList = new PriorityQueue<>(Comparator.comparingDouble(Path :: getTrueCost));
        Set<Vertex> closedList = new HashSet<>();

        while(!openList.isEmpty()){
            Path currentPath = openList.poll();
            Vertex current = currentPath.getDestination();

            if(current.equals(dst)){
                return currentPath;
            }

            closedList.add(current);
            for(Vertex neighbor : getNeighbors(current, state, extraParams)){
                if(!closedList.contains(neighbor)){
                    Path newPath = new Path(neighbor, getEdgeWeight(current, dst, state, extraParams), currentPath);
                    openList.add(newPath);
                }
            }
        }

        return null;
    }

    public float getEdgeWeight(Vertex src,
                               Vertex dst,
                               StateView state,
                               ExtraParams extraParams)
    {
        //path closer to enemy = higher weight
        //path further = lower weight.
        //should be heuristic function here
        return 1f;
    }

    public boolean shouldReplacePlan(StateView state,
                                     ExtraParams extraParams)
    {
        return false;
    }

    //////////////////////////////// End of AStarAgent methods to override ///////////////////////////////

}

