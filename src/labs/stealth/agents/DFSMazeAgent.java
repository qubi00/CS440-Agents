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

        q.add(new Path(src, 0f, null));
        visited.add(src);

        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
            Direction.NORTHEAST, Direction.NORTHWEST, Direction.SOUTHEAST, Direction.SOUTHWEST};
        
        while(!q.isEmpty()){
            Path currPath = q.pop();
            Vertex currVertex = currPath.getDestination();
            int X = currVertex.getXCoordinate();
            int Y = currVertex.getYCoordinate();

            if(currVertex.equals(goal)){
                return currPath;
            }

            
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
                if(state.inBounds(newX, newY) && !state.isResourceAt(newX, newY) && !visited.contains(new Vertex(newX, newY))){
                    Vertex neighbor = new Vertex(newX, newY);
                    visited.add(neighbor);
                    Path newPath = new Path(neighbor, 1.0f, currPath);
                    q.add(newPath);
                }
            }
        }
        return null;
    }

}
