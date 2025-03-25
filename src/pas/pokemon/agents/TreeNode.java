package src.pas.pokemon.agents;

import edu.bu.labs.pokemon.core.Battle;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.utils.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public class TreeNode {
    public enum NodeType{
        MAX, 
        MIN, 
        MOVE_ORDER_CHANCE,
        MOVE_RESOLUTION_CHANCE,
        POST_TURN_CHANCE
    };

    private BattleView state;
    private MoveView lastMove;
    private double probability;
    private List<TreeNode> children; //future states
    private NodeType type;

    public TreeNode(BattleView state, MoveView move, double probability, NodeType type) {
        this.state = state;
        this.lastMove = move;
        this.probability = probability;
        this.children = new ArrayList<>();
        this.type = type;
    }


    public BattleView getState() {return state;}
    public MoveView getMove() {return lastMove;}
    public double getProbability() {return probability;}
    public List<TreeNode> getChildren() {return children;}
    public NodeType getType() {return type;}

    //expand all possible moves for decision nodes
    public void expandDecision(int teamIdx) {
        List<MoveView> legalMoves = Arrays.asList(state.getTeamView(teamIdx).getPokemonView(0).getMoveViews());

        for (MoveView move : legalMoves) {
            List<Pair<Double, BattleView>> outcomes = move.getPotentialEffects(state, 0, 0);

            if(outcomes.size() == 1 && outcomes.get(0).getFirst() == 1.0){
                Pair<Double, BattleView> outcome = outcomes.get(0);
                //add decision node
                children.add(new TreeNode(outcome.getSecond(), move, outcome.getFirst(), this.type));
            }else{
                outcomes.sort((a, b) -> Double.compare(b.getFirst(), a.getFirst()));

                int numBest = 5;
                int count = 0;

                for (Pair<Double, BattleView> outcome : outcomes) {
                    if(count >= numBest){
                        break;
                    }
                    double prob = outcome.getFirst();
                    BattleView nextState = outcome.getSecond();
                    //node for when move resolves
                    children.add(new TreeNode(nextState, move, prob, NodeType.MOVE_RESOLUTION_CHANCE));
                    count++;
                }
            }

        }
    }

    public List<Pair<Double, BattleView>> getMoveResolutionOutcomes(BattleView state, MoveView move){
        double hitProb = move.getAccuracy(); 
        List<Pair<Double, BattleView>> outcomes = new ArrayList<>();
        outcomes.add(new Pair<>(hitProb, state));
        outcomes.add(new Pair<>(1-hitProb, state));
        return outcomes;
    }

    public void expandMoveResolution(){
        List<Pair<Double, BattleView>> outcomes = getMoveResolutionOutcomes(state, lastMove);
        for(Pair<Double, BattleView> outcome: outcomes){
            children.add(new TreeNode(outcome.getSecond(), lastMove, outcome.getFirst(), NodeType.POST_TURN_CHANCE));
        }
    }


    public double getProbability(BattleView state){
        //currently 50/50. should add speed checking
        return .5;
    }

    public void expandMoveOrder(int teamIdx){
        double probPlayer = getProbability(state);
        double probEnemy = 1 - probPlayer;

        TreeNode playerNode = new TreeNode(state, null, probPlayer, NodeType.MAX);
        TreeNode enemyNode = new TreeNode(state, null, probEnemy, NodeType.MIN);

        children.add(playerNode);
        children.add(enemyNode);
    }

    public void expandPostTurn(int teamIdx){
        if(isTerminal()){
            return;
        }
        //new turn. check if tree expansion expands beyond their turn.
        children.add(new TreeNode(state, null, 1, NodeType.MOVE_ORDER_CHANCE));
    }


    public void expand(int teamIdx){
        switch(this.type){
            case MAX:
                expandDecision(teamIdx);
                break;
            case MIN:
                expandDecision(teamIdx);
                break;
            case MOVE_ORDER_CHANCE:
                expandMoveOrder(teamIdx);
                break;
            case MOVE_RESOLUTION_CHANCE:
                expandMoveResolution();
                break;
            case POST_TURN_CHANCE:
                expandPostTurn(teamIdx);
                break;
        }

    }

    //checks if either the enemy loses or we lose
    public boolean isTerminal() {
        return state.getTeamView(0).size() == 0 || state.getTeamView(1).size() == 0;
    }
}
