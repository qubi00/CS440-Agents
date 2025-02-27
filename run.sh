#!/bin/bash

# Number of games to simulate
NUM_GAMES=100
SUCCESS_COUNT=0

# Path to game XML file (change this to the correct map file path)
MAP_FILE="data/pas/stealth/OneUnitSmallMaze.xml"

# Compile the code
javac -cp "./lib/*:." @pas-stealth.srcs

# Run the game NUM_GAMES times
for ((i=1; i<=NUM_GAMES; i++))
do
    echo "Running game $i..."
    OUTPUT=$(java -cp "./lib/*:." edu.cwru.sepia.Main2 $MAP_FILE)
    
    # Check console output for success message (customize if your success message is different)
    if echo "$OUTPUT" | grep -q "Mission Successful"; then
        ((SUCCESS_COUNT++))
    fi
done

# Print success rate
echo "Total games simulated: $NUM_GAMES"
echo "Successful games: $SUCCESS_COUNT"
echo "Success rate: $((SUCCESS_COUNT * 100 / NUM_GAMES))%"