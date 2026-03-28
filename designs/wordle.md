# Wordle Scoreboard Design

### Notes
- these codeblocks have the correct spacing on emoji and non-emoji lines in discord  on ios. there are no alignment issues
- if neither player has submitted a result for wordle when the scoreboards are posted, no wordle scoreboard will be posted

## Scenario 1 - Tie by win
```
 Wordle #1234
 
-----------------------------------
 William  - 6  |   Conor - 6           
-----------------------------------
 ⬛⬛⬛🟨⬛   |   ⬛⬛⬛🟨⬛     
 ⬛⬛⬛⬛🟨   |   ⬛⬛⬛⬛🟨     
 🟨🟨🟩⬛⬛   |   🟨🟨🟩⬛⬛     
 🟩🟩🟩🟩⬛   |   🟩🟩🟩🟩⬛     
 ⬛🟨🟩🟨⬛   |   ⬛🟨🟩🟨⬛     
 🟩🟩🟩🟩🟩   |   🟩🟩🟩🟩🟩      
-----------------------------------
 🤝 Tie!
-----------------------------------
```
### Notes
- result message: 🤝 Tie!
- happens when both players have the same number of guesses, or both lose.

## Scenario 2 - Tie by loss
```
 Wordle #1234
 
-----------------------------------
 William  - X  |   Conor - X           
-----------------------------------
 ⬛⬛⬛🟨⬛   |   ⬛⬛⬛🟨⬛     
 ⬛⬛⬛⬛🟨   |   ⬛⬛⬛⬛🟨     
 🟨🟨🟩⬛⬛   |   🟨🟨🟩⬛⬛     
 🟩🟩🟩🟩⬛   |   🟩🟩🟩🟩⬛     
 ⬛🟨🟩🟨⬛   |   ⬛🟨🟩🟨⬛     
 🟩🟩🟩🟩⬛   |   🟩🟩🟩🟩⬛      
-----------------------------------
 🤝 Tie!
-----------------------------------
```
### Notes
- X represents an incomplete Wordle score
- result message is the same as tie by win

## Scenario 3 - Winner declared - both complete
```
 Wordle #1234
 
-----------------------------------
 William  - 6  |   Conor - 4           
-----------------------------------
 ⬛⬛⬛🟨⬛   |   ⬛⬛⬛🟨⬛     
 ⬛⬛⬛⬛🟨   |   ⬛⬛⬛⬛🟨     
 🟨🟨🟩⬛⬛   |   🟨🟨🟩⬛⬛     
 🟩🟩🟩🟩⬛   |   🟩🟩🟩🟩🟩     
 ⬛🟨🟩🟨⬛   |
 🟩🟩🟩🟩⬛   | 
-----------------------------------
 🏆 Conor wins! (-2)
-----------------------------------
```
### Notes
- The player with more emoji rows goes on the left, always
- result message: 🏆 Conor wins! (Difference in number of guesses)

## Scenario 4 - Winner declared - one loss
```
 Wordle #1234
 
-----------------------------------
 Conor  - X    |   William - 4           
-----------------------------------
 ⬛⬛⬛🟨⬛   |   ⬛⬛⬛🟨⬛     
 ⬛⬛⬛⬛🟨   |   ⬛⬛⬛⬛🟨     
 🟨🟨🟩⬛⬛   |   🟨🟨🟩⬛⬛     
 🟩🟩🟩🟩⬛   |   🟩🟩🟩🟩🟩     
 ⬛🟨🟩🟨⬛   |
 🟩🟩🟩🟩⬛   | 
-----------------------------------
 🏆 William wins!
-----------------------------------
```
### Notes
- The player with more emoji rows goes on the left, always
- result message: 🏆 William wins!
- no score differential in result message

## Scenario 5 - Only one result submitted
```
 Wordle #1234
 
-----------------------------------
 Conor  - X          
-----------------------------------
 ⬛⬛⬛🟨⬛   
 ⬛⬛⬛⬛🟨   
 🟨🟨🟩⬛⬛   
 🟩🟩🟩🟩⬛   
 ⬛🟨🟩🟨⬛   
 🟩🟩🟩🟩⬛   
-----------------------------------
 ⏳ William hasn't submitted
-----------------------------------
```
### Notes
- only the submitted player is shown
- result message: ⏳ William hasn't submitted  
- No centre divider in table
- width is still 35 character