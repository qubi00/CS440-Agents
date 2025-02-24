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
    private boolean townhallDestroyed = false;
    private Vertex startingPos;
    

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

        UnitView myUnit = state.getUnit(this.getMyUnitID());
        this.startingPos = new Vertex(myUnit.getXPosition(), myUnit.getYPosition());

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
        Vertex currentPos = new Vertex(state.getUnit(unitId).getXPosition(), state.getUnit(unitId).getYPosition());
        Vertex townhall = null;

        if(phase == AgentPhase.INFILTRATE){
            UnitView enemyBase = state.getUnit(getEnemyBaseUnitID());
            if(enemyBase != null){
                townhall = new Vertex(state.getUnit(getEnemyBaseUnitID()).getXPosition(),state.getUnit(getEnemyBaseUnitID()).getYPosition());
            }else{
                townhallDestroyed = true;
                phase = AgentPhase.EXFILTRATE;
            }
        }
        if(this.shouldReplacePlan(state, null))
        {
            Vertex goal = null;
            if(phase == AgentPhase.INFILTRATE){
                goal = townhall;
            }else if(phase == AgentPhase.EXFILTRATE){
                goal = startingPos;
            }
            if(goal != null){
                currentPath = aStarSearch(currentPos, goal, state, null);
            }
            
        }

        if(currentPath != null && !currentPos.equals(currentPath.getDestination())){
            //should be immediate move rather than last move
            Vertex nextMove = nextMove(currentPos);
            if(nextMove != null){
                Direction nextDir = getDirectionToMoveTo(currentPos, nextMove);
                actions.put(unitId, Action.createPrimitiveMove(unitId, nextDir));
            }
        }else{
            //agent is in infiltrate plan, so it will attack. when it is in exfiltrate plan,
            //once we finish traversing path we should be at starting point
            if(phase == AgentPhase.INFILTRATE){
                int townhallId = getEnemyBaseUnitID();
                if(townhallId != -1){
                    actions.put(unitId, Action.createCompoundAttack(unitId, townhallId));
                }
            }
        }

        return actions;
    }

    ////////////////////////////////// End of Sepia methods to override //////////////////////////////////
    
    public Vertex nextMove(Vertex currentPos){
        Path temp = this.currentPath;
        while(temp.getParentPath() != null){
            if(temp.getParentPath().getDestination().equals(currentPos)){
                return temp.getDestination();
            }
            temp = temp.getParentPath();
        }
        return null;
    }

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

        Path start = new Path(src, 0f, null);
        openList.add(start);

        while(!openList.isEmpty()){
            Path currentPath = openList.poll();
            Vertex current = currentPath.getDestination();

            if(current.equals(dst)){
                return currentPath;
            }

            closedList.add(current);
            for(Vertex neighbor : getNeighbors(current, state, extraParams)){
                if(!closedList.contains(neighbor)){
                    float cost = getEdgeWeight(current, dst, state, extraParams);
                    Path newPath = new Path(neighbor, currentPath.getTrueCost() + cost, currentPath);
                    openList.add(newPath);
                }
            }
        }

        return null;
    }

    public static float calculateDistance(int x1, int y1, int x2, int y2) {
        return (float)Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    public float getEdgeWeight(Vertex src,
                               Vertex dst,
                               StateView state,
                               ExtraParams extraParams)
    {
        //path closer to enemy = higher weight
        //path further = lower weight.
        //should be heuristic function here
        float base = 1f;
        float riskCost = 0f;

        for(Integer enemyId: this.getOtherEnemyUnitIDs()){
            UnitView enemy = state.getUnit(enemyId);
            float distanceCalc = calculateDistance(src.getXCoordinate(), src.getYCoordinate(), enemy.getXPosition(), enemy.getYPosition());
            if(distanceCalc < this.enemyChebyshevSightLimit){
                riskCost += ((this.enemyChebyshevSightLimit - distanceCalc) * 5);
            }
        }
        float goalDist = calculateDistance(src.getXCoordinate(), src.getYCoordinate(), dst.getXCoordinate(), dst.getYCoordinate());
        return base + riskCost + goalDist;

    }

    public boolean shouldReplacePlan(StateView state,
                                     ExtraParams extraParams)
    {
        int unitId = this.getMyUnitID();
        UnitView myUnit = state.getUnit(unitId);
        Vertex currentPos = new Vertex(myUnit.getXPosition(), myUnit.getYPosition());
        if(this.currentPath == null || currentPos.equals(currentPath.getDestination())){
            return true;
        }else{
            return false;
        }
    }

    //////////////////////////////// End of AStarAgent methods to override ///////////////////////////////

}

