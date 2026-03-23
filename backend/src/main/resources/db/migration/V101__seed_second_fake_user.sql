INSERT INTO users (id, provider, provider_user_id, email, display_name)
VALUES (2, 'fake', 'fake-user-2', 'user2@test.com', 'Test User 2');

-- Reset the sequence to avoid conflicts
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
