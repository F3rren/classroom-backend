-- Primo script da eseguire
-- Table: public.utenti
CREATE SEQUENCE IF NOT EXISTS utenti_id_seq;

CREATE TABLE IF NOT EXISTS public.utenti
(
    id bigint NOT NULL DEFAULT nextval('utenti_id_seq'::regclass),
    username character varying(50) COLLATE pg_catalog."default" NOT NULL,
    password character varying(255) COLLATE pg_catalog."default" NOT NULL,
    nome character varying(100) COLLATE pg_catalog."default" NOT NULL,
    email character varying(100) COLLATE pg_catalog."default" NOT NULL,
    ruolo character varying(20) COLLATE pg_catalog."default" NOT NULL,
    data_registrazione timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    ultimo_accesso timestamp without time zone,
    CONSTRAINT utente_pkey PRIMARY KEY (id),
    CONSTRAINT utente_email_key UNIQUE (email),
    CONSTRAINT utente_username_key UNIQUE (username),
    CONSTRAINT utente_ruolo_check CHECK (ruolo::text = ANY (ARRAY['admin'::character varying, 'user'::character varying]::text[]))
);

-- Script di inserimento utenti di esempio
-- NOTA: Le password sono in chiaro solo per esempio. In produzione usa BCrypt!
-- Utenti admin
INSERT INTO public.utenti (username, password, nome, email, ruolo, data_registrazione, ultimo_accesso) VALUES
('admin', 'admin123', 'Amministratore Sistema', 'admin@prenotazioni.it', 'admin', CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '2 hours'),
('mario.rossi', 'password123', 'Mario Rossi', 'mario.rossi@prenotazioni.it', 'admin', CURRENT_TIMESTAMP - INTERVAL '25 days', CURRENT_TIMESTAMP - INTERVAL '1 day');
-- Utenti normali
INSERT INTO public.utenti (username, password, nome, email, ruolo, data_registrazione, ultimo_accesso) VALUES
('laura.bianchi', 'password123', 'Laura Bianchi', 'laura.bianchi@studenti.it', 'user', CURRENT_TIMESTAMP - INTERVAL '20 days', CURRENT_TIMESTAMP - INTERVAL '3 hours');
