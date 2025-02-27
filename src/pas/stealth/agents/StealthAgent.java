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
import java.util.List;


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
    private int enemyChebyshevSightLimit;
    private Path currentPath;
    private AgentPhase phase;
    private Vertex startingPos;
    private List<Vertex> currentRoute;
    private int routeIndex;

    //for path risk calc
    private float riskMultiplier;
    //risk weight should be a little higher than the cap, so if it's adjacent
    //then it will avoid the path
    private float riskWeight;
    private float dangerCap;

    private float unitRange = 5f;
    

    public StealthAgent(int playerNum)
    {
        super(playerNum);

        this.enemyChebyshevSightLimit = -1; // invalid value....we won't know this until initialStep()
        phase = AgentPhase.INFILTRATE;
    }

    public final int getEnemyChebyshevSightLimit() { return this.enemyChebyshevSightLimit; }

    public void setEnemyChebyshevSightLimit(int i) { this.enemyChebyshevSightLimit = i; }


    ///////////////////////////////////////// Sepia methods to override ///////////////////////////////////

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

        //calculate the risk and danger cap ratio
        float x = state.getXExtent();
        float y = state.getYExtent();
        float scale = (x+y)/2f;

        riskMultiplier = scale * 22;
        riskWeight = scale * 42f;
        dangerCap = scale * 32f;

        System.out.println(riskMultiplier);
        System.out.println(riskWeight);
        System.out.println(dangerCap);


        return null;
    }


    @Override
    public Map<Integer, Action> middleStep(StateView state,
                                           HistoryView history)
    {
        Map<Integer, Action> actions = new HashMap<Integer, Action>();

        int unitId = this.getMyUnitID();
        Vertex currentPos = new Vertex(state.getUnit(unitId).getXPosition(), state.getUnit(unitId).getYPosition());
        Vertex townhall = null;

        if(phase == AgentPhase.INFILTRATE){
            UnitView enemyBase = state.getUnit(getEnemyBaseUnitID());
            if(enemyBase != null){
                townhall = new Vertex(enemyBase.getXPosition(),enemyBase.getYPosition());
            }else{
                phase = AgentPhase.EXFILTRATE;
            }
        }

        Vertex goal = null;
        if(this.shouldReplacePlan(state, null)){
            if(phase == AgentPhase.INFILTRATE){
                goal = townhall;
            }else if(phase == AgentPhase.EXFILTRATE){
                goal = startingPos;
            }
            if(goal != null){
                //currentPath should return a path assuming enemies are obstacles if within danger zone
                currentPath = aStarSearch(currentPos, goal, state, null);
                routeIndex = 0;
                currentRoute = pathToList(currentPath);
            }
        }

        //attack townhall if it exists, we are in infiltrate mode, and the townhall is right next to us
        if(phase == AgentPhase.INFILTRATE && townhall != null && isAdjacent(currentPos, townhall)){
            int townhallId = getEnemyBaseUnitID();
            if(townhallId != -1){
                actions.put(unitId, Action.createPrimitiveAttack(unitId, townhallId));
            }
            return actions;
        }


        if(currentPath != null && !currentPos.equals(currentPath.getDestination())){
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
                    actions.put(unitId, Action.createPrimitiveAttack(unitId, townhallId));
                }
            }
        }

        return actions;
    }

    ////////////////////////////////// End of Sepia methods to override //////////////////////////////////
    
    public Vertex nextMove(Vertex currentPos){
        if(currentRoute == null || currentRoute.isEmpty()){
            return null;
        }

        //refind the index(swapping routes might have this issue)
        if(!currentRoute.get(routeIndex).equals(currentPos)){
            for(int i = 0; i < currentRoute.size(); i++){
                if(currentRoute.get(i).equals(currentPos)){
                    routeIndex = i;
                    break;
                }
            }
        }

        if(routeIndex + 1 < currentRoute.size()){
            routeIndex += 1;
            return currentRoute.get(routeIndex);
        }
        return null;
    }

    public List<Vertex> pathToList(Path p){
        List<Vertex> route = new ArrayList<>();
        while(p != null){
            route.add(0, p.getDestination());
            p = p.getParentPath();
        }
        return route;
    }

    /////////////////////////////////// AStarAgent methods to override ///////////////////////////////////
    
    public boolean isValidMove(Vertex v, StateView state){
        return(v.getXCoordinate() >= 0 && v.getYCoordinate() >= 0 && 
        v.getXCoordinate() < state.getXExtent() && v.getYCoordinate() < state.getYExtent());
    }

    public boolean isPathSafe(Path p, StateView state){
        Path temp = p;
        while(temp != null){
            if(calculateRisk(temp.getDestination(), state) > dangerCap){
                return false;
            }
            temp = temp.getParentPath();
        }
        return true;
    }

    public Collection<Vertex> getNeighbors(Vertex v,
                                           StateView state,
                                           ExtraParams extraParams)
    {   
        UnitView enemyBase = state.getUnit(getEnemyBaseUnitID());
        Vertex townhallPos = null;
        if(enemyBase != null){
            townhallPos = new Vertex(enemyBase.getXPosition(), enemyBase.getYPosition());
        }

        Collection<Vertex> neighbors = new ArrayList<>();
        Vertex neighbor = null;

        for (Direction dir : Direction.values()){
            int x = v.getXCoordinate();
            int y = v.getYCoordinate();
            switch(dir){
                case NORTH: y -= 1; 
                break;
                case SOUTH: y += 1; 
                break;
                case EAST: x += 1; 
                break;
                case WEST: x -= 1; 
                break;
                case NORTHEAST: x += 1; y -= 1; 
                break;
                case NORTHWEST: x -= 1; y -= 1;
                break;
                case SOUTHEAST: x += 1; y += 1;
                break;
                case SOUTHWEST: x -= 1; y += 1;
                break;

            }
            neighbor = new Vertex(x, y);

            //not valid move or too dangerous
            if(!isValidMove(neighbor, state) || calculateRisk(neighbor, state) > dangerCap){
                    continue;
            }            

            //found the townhall or if no resources, then it's a valid neighbor
            if((townhallPos != null && neighbor.equals(townhallPos)) ||
            !state.isResourceAt(neighbor.getXCoordinate(), neighbor.getYCoordinate())){
                neighbors.add(neighbor);
            }
            
        }
        return neighbors;
    }


    public float heuristic(Vertex src, Vertex dst, StateView state){
        int goalDist = calculateDistance(src, dst);
        float enemyRisk = 0f;
        
        for (Integer enemyId : getOtherEnemyUnitIDs()) {
            UnitView enemy = state.getUnit(enemyId);
            Vertex enemyPos = new Vertex(enemy.getXPosition(), enemy.getYPosition());
            int enemyDist = calculateDistance(src, enemyPos);
            enemyRisk += (1f / enemyDist);
        }
        return (goalDist + riskMultiplier * enemyRisk);

    }

    public boolean isAdjacent(Vertex v1, Vertex v2) {
        return Math.abs(v1.getXCoordinate() - v2.getXCoordinate()) <= 1 &&
               Math.abs(v1.getYCoordinate() - v2.getYCoordinate()) <= 1;
    }

    public Path aStarSearch(Vertex src,
                            Vertex dst,
                            StateView state,
                            ExtraParams extraParams)
    {
        PriorityQueue<Path> openList = new PriorityQueue<>(Comparator.comparingDouble(path -> path.getTrueCost()  + getHeuristicValue(path.getDestination(), dst, state)));
        Set<Vertex> closedList = new HashSet<>();
        Map<Vertex, Float> currentCost = new HashMap<>();

        Path start = new Path(src, 0f, null);
        openList.add(start);
        currentCost.put(src, 0f);

        while(!openList.isEmpty()){
            Path currentPath = openList.poll();
            Vertex current = currentPath.getDestination();

            if(current.equals(dst)){
                return currentPath;
            }

            closedList.add(current);

            for(Vertex neighbor : getNeighbors(current, state, extraParams)){
                if(closedList.contains(neighbor)){
                    continue;
                }
                float cost = getEdgeWeight(current, neighbor, state, extraParams);
                float newTotalCost = currentPath.getTrueCost() + cost;
                if(!currentCost.containsKey(neighbor) || newTotalCost < currentCost.get(neighbor)){
                    currentCost.put(neighbor, newTotalCost);
                    Path newPath = new Path(neighbor, newTotalCost, currentPath);
                    openList.add(newPath);
                }
                
            }
        }

        return currentPath;
    }

    public static int calculateDistance(Vertex agent, Vertex enemy) {
        return Math.max(Math.abs(agent.getXCoordinate() - enemy.getXCoordinate()), Math.abs(agent.getYCoordinate() - enemy.getYCoordinate()));
    }

    public float calculateRisk(Vertex v, StateView state){
        float totalRisk = 0f;
        for(Integer enemyId: getOtherEnemyUnitIDs()){
            UnitView enemy = state.getUnit(enemyId);
            Vertex enemyPos = new Vertex(enemy.getXPosition(), enemy.getYPosition());

            //enemies too far dont matter
            int distanceCalc = calculateDistance(v, enemyPos);
            if(distanceCalc > unitRange){
                continue;
            }

            totalRisk += riskWeight/(float)distanceCalc;
        }
        return totalRisk;
    }

    public float getEdgeWeight(Vertex src,
                               Vertex dst,
                               StateView state,
                               ExtraParams extraParams)
    {
        //path closer to enemy = higher weight
        //path further = lower weight.
        float base = 1f;
        float riskCost = calculateRisk(dst, state);
        int goalDist = calculateDistance(src, dst);
        return base + riskCost + (.5f * goalDist);

    }

    public boolean shouldReplacePlan(StateView state,
                                     ExtraParams extraParams)
    {
        int unitId = this.getMyUnitID();
        UnitView myUnit = state.getUnit(unitId);
        Vertex currentPos = new Vertex(myUnit.getXPosition(), myUnit.getYPosition());
        Vertex goal = null;

        if(phase == AgentPhase.INFILTRATE){
            UnitView enemyBase = state.getUnit(getEnemyBaseUnitID());
            if(enemyBase != null){
                goal = new Vertex(enemyBase.getXPosition(), enemyBase.getYPosition());
            }else{
                goal = startingPos;
            }
        }else if(phase == AgentPhase.EXFILTRATE){
            goal = startingPos;
        }

        if(this.currentPath == null || currentPos.equals(currentPath.getDestination())
        || !currentPath.getDestination().equals(goal) || !isPathSafe(currentPath, state)){
            return true;
        }else{
            return false;
        }
    }

    //////////////////////////////// End of AStarAgent methods to override ///////////////////////////////

}

