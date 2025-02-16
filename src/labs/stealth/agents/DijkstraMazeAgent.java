package src.labs.stealth.agents;

// SYSTEM IMPORTS
import edu.bu.labs.stealth.agents.MazeAgent;
import edu.bu.labs.stealth.graph.Vertex;
import edu.bu.labs.stealth.graph.Path;


import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.util.Direction;                           // Directions in Sepia


import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.PriorityQueue; // heap in java
import java.util.Queue;
import java.util.Set;


// JAVA PROJECT IMPORTS


public class DijkstraMazeAgent
    extends MazeAgent
{

    public DijkstraMazeAgent(int playerNum)
    {
        super(playerNum);
    }


    private float getWeight(Direction dir){
        float weight = 0;
        switch(dir){
            case NORTH:
                weight = 10f;
                break;
            case SOUTH:
                weight = 1f;
                break;
            case EAST:
                weight = 5f;
                break;
            case WEST:
                weight = 5f;
                break;
            case NORTHEAST:
                weight = (float) Math.sqrt(10 * 10 + 5 * 5);
                break;
            case NORTHWEST:
                weight = (float) Math.sqrt(10 * 10 + 5 * 5);
                break;
            case SOUTHEAST:
                weight = (float) Math.sqrt(1 * 1 + 5 * 5);
                break;
            case SOUTHWEST:
                weight = (float) Math.sqrt(1 * 1 + 5 * 5);
                break;
        }
        return weight;
    }


    @Override
    public Path search(Vertex src,
                       Vertex goal,
                       StateView state)
    {   

        PriorityQueue<Path> pq = new PriorityQueue<>(Comparator.comparing(Path::getTrueCost));
        Set<Vertex> visited = new HashSet<>();
        Map<Vertex, Float> distance = new HashMap<>();

        pq.add(new Path(src, 0f, null));
        distance.put(src, 0f);

        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
            Direction.NORTHEAST, Direction.NORTHWEST, Direction.SOUTHEAST, Direction.SOUTHWEST};
        
        while(!pq.isEmpty()){
            Path currPath = pq.poll();
            Vertex currVertex = currPath.getDestination();
            Float currCost = currPath.getTrueCost();

            if(visited.contains(currVertex)){
                continue;
            }
            visited.add(currVertex);


            if(currVertex.equals(goal)){
                System.out.println(currPath.toString());
                return currPath;
            }
            
            int currRow = currVertex.getXCoordinate();
            int currCol = currVertex.getYCoordinate();
            
            for(int i = 0; i < directions.length; i++){
                Direction dir = directions[i];
                int newRow = currRow;
                int newCol = currCol;
                if (dir == Direction.NORTH) {
                    newRow -= 1;
                } else if (dir == Direction.SOUTH) {
                    newRow += 1;
                } else if (dir == Direction.WEST) {
                    newCol -= 1;
                } else if (dir == Direction.EAST) {
                    newCol += 1;
                } else if (dir == Direction.NORTHEAST) {
                    newRow -= 1;
                    newCol += 1;
                } else if (dir == Direction.NORTHWEST) {
                    newRow -= 1;
                    newCol -= 1;
                } else if (dir == Direction.SOUTHEAST) {
                    newRow += 1;
                    newCol += 1;
                } else if (dir == Direction.SOUTHWEST) {
                    newRow += 1;
                    newCol -= 1;
                }
                if(state.inBounds(newRow, newCol) && !state.isResourceAt(newRow, newCol)){
                    Vertex neighbor = new Vertex(newRow, newCol);
                    float weight = getWeight(dir);
                    float newCost = currCost + weight;

                    if(!distance.containsKey(neighbor) || newCost < distance.get(neighbor)){
                        distance.put(neighbor, newCost);
                        Path newPath = new Path(neighbor, newCost, currPath);
                        pq.add(newPath);
                    }
                }
            }
        }
        return null;
    }

}
