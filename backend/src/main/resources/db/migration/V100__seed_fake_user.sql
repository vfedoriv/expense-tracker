INSERT INTO users (id, provider, provider_user_id, email, display_name)
VALUES (1, 'fake', 'fake-user-1', 'admin@test.com', 'Test User');

-- Reset the sequence to avoid conflicts with the seeded user
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
