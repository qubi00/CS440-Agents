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
                weight = (float) Math.sqrt((10 * 10) + (5 * 5));
                break;
            case NORTHWEST:
                weight = (float) Math.sqrt((10 * 10) + (5 * 5));
                break;
            case SOUTHEAST:
                weight = (float) Math.sqrt((1 * 1) + (5 * 5));
                break;
            case SOUTHWEST:
                weight = (float) Math.sqrt((1 * 1) + (5 * 5));
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
        Map<Vertex, Float> distance = new HashMap<>();

        pq.add(new Path(src, 0f, null));
        distance.put(src, 0f);

        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
            Direction.NORTHEAST, Direction.NORTHWEST, Direction.SOUTHEAST, Direction.SOUTHWEST};
            
        while(!pq.isEmpty()){
            Path currPath = pq.poll();
            Vertex currVertex = currPath.getDestination();
            Float currCost = currPath.getTrueCost();


            if(currVertex.equals(goal)){
                System.out.println(currPath.toString());
                System.out.println(currCost.toString());
                return currPath;
            }
            
            int X = currVertex.getXCoordinate();
            int Y = currVertex.getYCoordinate();
            
            for(int i = 0; i < directions.length; i++){
                Direction dir = directions[i];
                int newX = X;
                int newY = Y;
                if (dir == Direction.NORTH) {
                    newY -= 1;
                } else if (dir == Direction.SOUTH) {
                    newY += 1;
                } else if (dir == Direction.WEST) {
                    newX -= 1;
                } else if (dir == Direction.EAST) {
                    newX += 1;
                } else if (dir == Direction.NORTHEAST) {
                    newY -= 1;
                    newX += 1;
                } else if (dir == Direction.NORTHWEST) {
                    newY -= 1;
                    newX -= 1;
                } else if (dir == Direction.SOUTHEAST) {
                    newY += 1;
                    newX += 1;
                } else if (dir == Direction.SOUTHWEST) {
                    newY += 1;
                    newX -= 1;
                }
                if(state.inBounds(newX, newY) && !state.isResourceAt(newX, newY)){
                    Vertex neighbor = new Vertex(newX, newY);
                    float weight = getWeight(dir);
                    float newCost = currCost + weight;
                    System.out.println("weight:" + weight);
                    System.out.println("new cost:" + newCost);

                    if(!distance.containsKey(neighbor) || newCost < distance.get(neighbor)){
                        distance.put(neighbor, newCost);
                        Path newPath = new Path(neighbor, weight, currPath);
                        System.out.println("going to:" + neighbor + "weight: " + newCost);
                        pq.add(newPath);
                    }
                }
            }
        }
        return null;
    }

}
