-- Secondo script da eseguire
-- Table: public.aule
CREATE SEQUENCE IF NOT EXISTS aule_id_seq;

CREATE TABLE IF NOT EXISTS public.aule
(
    id bigint NOT NULL DEFAULT nextval('aule_id_seq'::regclass),
    nome character varying(100) COLLATE pg_catalog."default" NOT NULL,
    capienza integer NOT NULL,
    piano integer NOT NULL,
    is_virtual boolean NOT NULL DEFAULT false,
    descrizione text COLLATE pg_catalog."default",
    stato character varying(20) COLLATE pg_catalog."default" DEFAULT 'libera'::character varying,
    CONSTRAINT aula_pkey PRIMARY KEY (id),
    CONSTRAINT aula_nome_key UNIQUE (nome),
    CONSTRAINT aula_capienza_check CHECK (capienza > 0),
    CONSTRAINT aula_stato_check CHECK (stato::text = ANY (ARRAY['libera'::character varying, 'occupata'::character varying, 'bloccata'::character varying, 'manutenzione'::character varying]::text[]))
);
-- Script di inserimento aule di esempio
-- Aule piano terra (piano 0)
INSERT INTO public.aule (nome, capienza, piano, is_virtual, descrizione, stato) VALUES
('Aula Magna', 200, 0, false, 'Aula principale per conferenze e grandi eventi', 'libera'),
('Aula A1', 30, 0, false, 'Aula standard con proiettore e lavagna interattiva', 'libera'),
('Aula A2', 25, 0, false, 'Aula piccola per seminari', 'libera'),
('Lab Informatica 1', 40, 0, false, 'Laboratorio con 40 postazioni PC', 'libera');
-- Aule virtuali
INSERT INTO public.aule (nome, capienza, piano, is_virtual, descrizione, stato) VALUES
('Aula Virtuale 1', 100, 0, true, 'Aula virtuale per lezioni online - Piattaforma Teams', 'libera'),
('Aula Virtuale 2', 50, 0, true, 'Aula virtuale per webinar - Piattaforma Zoom', 'libera');
