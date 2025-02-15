package src.labs.stealth.agents;

// SYSTEM IMPORTS
import edu.bu.labs.stealth.agents.MazeAgent;
import edu.bu.labs.stealth.graph.Vertex;
import edu.bu.labs.stealth.graph.Path;


import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.util.Direction;

import java.util.HashSet;   // will need for dfs
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;     // will need for dfs
import java.util.Set;       // will need for dfs


// JAVA PROJECT IMPORTS


public class DFSMazeAgent
    extends MazeAgent
{

    public DFSMazeAgent(int playerNum)
    {
        super(playerNum);
    }

    @Override
    public Path search(Vertex src,
                       Vertex goal,
                       StateView state)
    {   

        Stack<Path> q = new Stack<>();
        Set<Vertex> visited = new HashSet<>();

        q.add(new Path(src, 1.0f, null));
        visited.add(src);

        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
            Direction.NORTHEAST, Direction.NORTHWEST, Direction.SOUTHEAST, Direction.SOUTHWEST};
        
        while(!q.isEmpty()){
            Path currPath = q.pop();
            Vertex currVertex = currPath.getDestination();
            int currRow = currVertex.getXCoordinate();
            int currCol = currVertex.getYCoordinate();

            if(currVertex.equals(goal)){
                System.out.println(currPath.toString());
                return currPath;
            }

            
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
                if(state.inBounds(newRow, newCol) && !state.isResourceAt(newRow, newCol) && !visited.contains(new Vertex(newRow, newCol))){
                    Vertex neighbor = new Vertex(newRow, newCol);
                    visited.add(neighbor);
                    Path newPath = new Path(neighbor, 1.0f, currPath);
                    q.add(newPath);
                }
            }
        }
        return null;
    }

}
