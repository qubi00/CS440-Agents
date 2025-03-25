package src.pas.pokemon.agents;

import edu.bu.labs.pokemon.core.Battle;
import edu.bu.pas.pokemon.core.Battle.BattleView;
import edu.bu.pas.pokemon.core.Move.MoveView;
import edu.bu.pas.pokemon.utils.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public class TreeNode {
    public enum NodeType{MAX, MIN, CHANCE};

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
                    children.add(new TreeNode(nextState, move, prob, NodeType.CHANCE));
                    count++;
                }
            }

        }
    }

    public void expandChance(){
        BattleView outcome1 = state;
        BattleView outcome2 = state;
        children.add(new TreeNode(outcome1, lastMove, .5, NodeType.MAX));
        children.add(new TreeNode(outcome2, lastMove, .5, NodeType.MIN));
    }


    public void expand(int teamIdx){
        if(this.type == NodeType.MAX || this.type == NodeType.MIN){
            expandDecision(teamIdx);
        }else if(this.type == NodeType.CHANCE){
            expandChance();
        }

    }

    //checks if either the enemy loses or we lose
    public boolean isTerminal() {
        return state.getTeamView(0).size() == 0 || state.getTeamView(1).size() == 0;
    }
}
