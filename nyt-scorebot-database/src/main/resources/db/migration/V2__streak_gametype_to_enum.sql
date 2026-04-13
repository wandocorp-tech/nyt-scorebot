-- Convert streak.game_type from display labels to enum names
UPDATE streak SET game_type = 'WORDLE' WHERE game_type = 'Wordle';
UPDATE streak SET game_type = 'CONNECTIONS' WHERE game_type = 'Connections';
UPDATE streak SET game_type = 'STRANDS' WHERE game_type = 'Strands';
