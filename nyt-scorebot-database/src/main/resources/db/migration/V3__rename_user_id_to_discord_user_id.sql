-- Rename app_user.user_id to discord_user_id for clarity
ALTER TABLE app_user RENAME COLUMN user_id TO discord_user_id;
