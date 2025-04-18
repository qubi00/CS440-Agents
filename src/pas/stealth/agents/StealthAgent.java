package src.pas.stealth.agents;


// SYSTEM IMPORTS
import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.environment.model.history.History.HistoryView;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.environment.model.state.ResourceNode;
import edu.cwru.sepia.environment.model.state.ResourceNode.ResourceView;
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
    private int numEnemies = 0;

    private Vertex goldUnitPos = null;
    private int goldId;
    private boolean obtainedGold;
    private int stepDepth;

    

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
        List<Integer> goldResourceNodeId = state.getResourceNodeIds(ResourceNode.Type.GOLD_MINE);
        this.goldId = goldResourceNodeId.iterator().next();
        ResourceView goldUnit = state.getResourceNode(this.goldId);
        goldUnitPos = new Vertex(goldUnit.getXPosition(), goldUnit.getYPosition());
        this.obtainedGold = false;

        stepDepth = 0;

        UnitView otherEnemyUnitView = null;
        Iterator<Integer> otherEnemyUnitIDsIt = this.getOtherEnemyUnitIDs().iterator();
        while(otherEnemyUnitIDsIt.hasNext() && otherEnemyUnitView == null)
        {
            otherEnemyUnitView = state.getUnit(otherEnemyUnitIDsIt.next());
            numEnemies++;

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


        Vertex currentPos = new Vertex(myUnit.getXPosition(), myUnit.getYPosition());
        Vertex goal = null;
        if(phase == AgentPhase.INFILTRATE) {
            if(!this.obtainedGold) {
                goal = goldUnitPos;
            } else {
                UnitView enemyBase = state.getUnit(getEnemyBaseUnitID());
                if(enemyBase != null) {
                    goal = new Vertex(enemyBase.getXPosition(), enemyBase.getYPosition());
                } else {
                    phase = AgentPhase.EXFILTRATE;
                }
            }
        }
        if(phase == AgentPhase.EXFILTRATE) {
            goal = startingPos;
        }
        if(goal != null) {
            currentPath = aStarSearch(currentPos, goal, state, null);
            routeIndex = 0;
            currentRoute = pathToList(currentPath);
        }
        
        Map<Integer, Action> actions = new HashMap<>();
        if(currentPath != null && !currentPos.equals(currentPath.getDestination())) {
            Vertex nextStep = nextMove(currentPos);
            if(nextStep != null) {
                Direction nextDir = getDirectionToMoveTo(currentPos, nextStep);
                actions.put(getMyUnitID(), Action.createPrimitiveMove(getMyUnitID(), nextDir));
                stepDepth ++;
            }
        }

        return actions;
    }


    @Override
    public Map<Integer, Action> middleStep(StateView state,
                                           HistoryView history)
    {
        Map<Integer, Action> actions = new HashMap<Integer, Action>();

        int unitId = this.getMyUnitID();
        Vertex currentPos = new Vertex(state.getUnit(unitId).getXPosition(), state.getUnit(unitId).getYPosition());
        Vertex townhall = null;
        Vertex goldPos = null;

        //gold block
        List<Integer> goldResourceNodeId = state.getResourceNodeIds(ResourceNode.Type.GOLD_MINE);
        if(goldResourceNodeId == null || goldResourceNodeId.isEmpty()){
            this.obtainedGold = true;
        } else {
            this.goldId = goldResourceNodeId.iterator().next();
            ResourceView goldUnit = state.getResourceNode(this.goldId);
            if(goldUnit == null){
                this.obtainedGold = true;
            }else{
                this.obtainedGold = false;
            }
        }
        

        if(phase == AgentPhase.INFILTRATE){
            UnitView enemyBase = state.getUnit(getEnemyBaseUnitID());
            if(this.obtainedGold == false){
                goldPos = this.goldUnitPos;
            }else if(enemyBase != null){
                townhall = new Vertex(enemyBase.getXPosition(),enemyBase.getYPosition());
            }else{
                phase = AgentPhase.EXFILTRATE;
            }
        }

        Vertex goal = null;
        if(this.shouldReplacePlan(state, null) || predict(currentPos, state)){
            if(phase == AgentPhase.INFILTRATE){
                if(this.obtainedGold == false){
                    goal = goldPos;
                }else{
                    goal = townhall;
                }
            }else if(phase == AgentPhase.EXFILTRATE){
                goal = startingPos;
            }
            if(goal != null){
                //currentPath should return a path assuming enemies are obstacles if within danger zone
                currentPath = aStarSearch(currentPos, goal, state, null);
                if(!isPathSafe(currentPath, state) && predict(currentPos, state)){
                    System.out.println("retreating");
                    Vertex safeRetreat = findSafeRetreat(currentPos, state);
                    System.out.println(safeRetreat);
                    currentPath = aStarSearch(currentPos, safeRetreat, state, null);
                }
                routeIndex = 0;
                currentRoute = pathToList(currentPath);
                stepDepth = 0;
                System.out.println(currentRoute);
            }
        }

        //attack townhall if it exists, we are in infiltrate mode, and the townhall is right next to us
        if(phase == AgentPhase.INFILTRATE && this.obtainedGold == false && isAdjacent(currentPos, goldPos)){
            if(goldId != -1){
                Direction collect = getDirectionToMoveTo(currentPos, goldPos);
                actions.put(unitId, Action.createPrimitiveGather(unitId, collect));
            }
            return actions;
        }else if(phase == AgentPhase.INFILTRATE && townhall != null && isAdjacent(currentPos, townhall)){
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
    
    public Vertex findSafeRetreat(Vertex currentPos, StateView state) {
        Vertex bestRetreat = currentPos;
        float maxDistance = 0;
        for(Vertex neighbor : getNeighbors(currentPos, state, null)) {
            float minEnemyDist = Float.MAX_VALUE;
            for (Integer enemyId : getOtherEnemyUnitIDs()) {
                UnitView enemy = state.getUnit(enemyId);
                if(enemy == null){
                    continue;
                }
                Vertex enemyPos = new Vertex(enemy.getXPosition(), enemy.getYPosition());
                float distance = (float)calculateDistance(neighbor, enemyPos);
                if(distance <= 3 && distance < minEnemyDist) {
                    minEnemyDist = distance;
                }
            }
            if(minEnemyDist > maxDistance) {
                maxDistance = minEnemyDist;
                bestRetreat = neighbor;
            }
        }
        return bestRetreat;
    }

    public boolean predict(Vertex currentPos, StateView state){
        for (Integer enemyId : getOtherEnemyUnitIDs()) {
            UnitView enemy = state.getUnit(enemyId);
            if (enemy == null) {
                continue;
            }
            Vertex enemyPos = new Vertex(enemy.getXPosition(), enemy.getYPosition());
            float distance = (float)calculateDistance(currentPos, enemyPos);
            if((distance - 1) <= enemyChebyshevSightLimit + 2){
                return true;
            }
        }
        return false;
    }
    
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

    public boolean isPathSafe(Path p, StateView state) {
        if (p == null) {
            return false;
        }
    
        for (Integer enemyId : getOtherEnemyUnitIDs()) {
            UnitView enemy = state.getUnit(enemyId);
            if (enemy == null) {
                continue;
            }
            Vertex enemyPos = new Vertex(enemy.getXPosition(), enemy.getYPosition());
    
            Path temp = p;
            while (temp != null) {
                Vertex stepPos = temp.getDestination();
                float distance = calculateEuclid(stepPos, enemyPos);
                if (distance <= 2.5) {
                    return false;
                }
                temp = temp.getParentPath();
            }
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

        List<Integer> goldResourceNodeIds = state.getResourceNodeIds(ResourceNode.Type.GOLD_MINE);
        Vertex goldUnitPos = null;
        if(goldResourceNodeIds != null && !goldResourceNodeIds.isEmpty()){
            int goldResourceId = goldResourceNodeIds.get(0);
            ResourceView goldRes = state.getResourceNode(goldResourceId);
            if(goldRes != null){
                goldUnitPos = new Vertex(goldRes.getXPosition(), goldRes.getYPosition());
            }
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

            //not in bound
            if(!isValidMove(neighbor, state)){
                continue;
            }
            
            //no tree, no gold or townhall
            //havent gotten gold, found gold
            //gotten gold, found townhall
            if(!state.isResourceAt(neighbor.getXCoordinate(), neighbor.getYCoordinate()) 
                && (goldUnitPos == null || !neighbor.equals(goldUnitPos)) 
                && (townhallPos == null || !neighbor.equals(townhallPos))) {
            neighbors.add(neighbor);
            }else if (!this.obtainedGold && goldUnitPos != null && neighbor.equals(goldUnitPos)){
                neighbors.add(neighbor);
            }else if (this.obtainedGold && townhallPos != null && neighbor.equals(townhallPos)){
                neighbors.add(neighbor);
            }
        }

        return neighbors;
    }


    public float heuristic(Vertex src, Vertex dst, StateView state){
        float heuristicCost = calculateDistance(src, dst);

        int riskZone = enemyChebyshevSightLimit + 2;
        float enemyRisk = 0f;

        for (Integer enemyId : getOtherEnemyUnitIDs()) {
            UnitView enemy = state.getUnit(enemyId);
            if(enemy == null){
                continue;
            }
            Vertex enemyPos = new Vertex(enemy.getXPosition(), enemy.getYPosition());
            float enemyDist = calculateEuclid(src, enemyPos);
            if(enemyDist <= riskZone){
                //further = less penalty
                enemyRisk += (riskZone - enemyDist) * 1000f;
            }
            
        }
        
        return (heuristicCost + enemyRisk);
    }

    public float getEdgeWeight(Vertex src,
                           Vertex dst,
                           StateView state,
                           ExtraParams extraParams)
    {   
        float dangerWeight = 0f;
        int numEnemiesNear = 0;
        for (Integer enemyId : getOtherEnemyUnitIDs()) {
            UnitView enemy = state.getUnit(enemyId);
            if (enemy != null) {
                Vertex enemyPos = new Vertex(enemy.getXPosition(), enemy.getYPosition());
                float distSrc = calculateEuclid(src, enemyPos);
                float distDst = calculateEuclid(dst, enemyPos);
                if (distDst < distSrc) {
                    numEnemiesNear++;
                    dangerWeight += 650f/distDst;
                }
            }
        }
        if(numEnemiesNear > 0){
            return dangerWeight/(numEnemiesNear);
        }
        return dangerWeight/(numEnemiesNear+1);
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
        
        Map<Vertex, Float> currentCost = new HashMap<>();
        Map<Vertex, Float> estimatedCost = new HashMap<>();


        PriorityQueue<Path> openList = new PriorityQueue<>(Comparator.comparingDouble(p -> estimatedCost.get(p.getDestination())));
        Set<Vertex> closedList = new HashSet<>();
        

        Path start = new Path(src, 0f, null);
        openList.add(start);
        currentCost.put(src, 0f);
        estimatedCost.put(src, heuristic(src, dst, state));

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
                if(cost == Float.MAX_VALUE){
                    continue;
                }
                float newTotalCost = currentCost.get(current) + cost;
                if(!currentCost.containsKey(neighbor) || newTotalCost < currentCost.get(neighbor)){
                    currentCost.put(neighbor, newTotalCost);
                    float estimatedDst = newTotalCost + heuristic(neighbor, dst, state);
                    estimatedCost.put(neighbor, estimatedDst);

                    Path newPath = new Path(neighbor, newTotalCost, currentPath);
                    openList.add(newPath);
                }
                
            }
        }

        return null;
    }

    public static int calculateDistance(Vertex agent, Vertex enemy) {
        return Math.max(Math.abs(agent.getXCoordinate() - enemy.getXCoordinate()), Math.abs(agent.getYCoordinate() - enemy.getYCoordinate()));
    }

    public static float calculateEuclid(Vertex agent, Vertex enemy) {
        return (float)Math.sqrt(Math.pow(agent.getXCoordinate() - enemy.getXCoordinate(), 2) + Math.pow(agent.getYCoordinate() - enemy.getYCoordinate(),2));
    }


    public boolean shouldReplacePlan(StateView state,
                                     ExtraParams extraParams)
    {
        Vertex goal = null;
        if(phase == AgentPhase.INFILTRATE){
            UnitView enemyBase = state.getUnit(getEnemyBaseUnitID());
            if(this.obtainedGold == false){
                goal = goldUnitPos;
            }else if(enemyBase != null){
                goal = new Vertex(enemyBase.getXPosition(), enemyBase.getYPosition());
            }else{
                goal = startingPos;
            }
        }else if(phase == AgentPhase.EXFILTRATE){
            goal = startingPos;
        }

        //only recompute when no path, reached destination, or not safe
        //previously were recomputing after reaching the last step of route
        if(this.currentPath == null || !currentPath.getDestination().equals(goal) 
        || !isPathSafe(currentPath, state)){
            return true;
        }else{
            return false;
        }
    }

    //////////////////////////////// End of AStarAgent methods to override ///////////////////////////////

}

