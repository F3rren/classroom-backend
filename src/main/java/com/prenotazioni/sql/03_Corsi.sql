-- Terzo script da eseguire
-- Table: public.corsi
CREATE SEQUENCE IF NOT EXISTS corsi_id_seq;

CREATE TABLE IF NOT EXISTS public.corsi
(
    id bigint NOT NULL DEFAULT nextval('corsi_id_seq'::regclass),
    nome character varying(100) COLLATE pg_catalog."default" NOT NULL,
    docente character varying(100) COLLATE pg_catalog."default" NOT NULL,
    descrizione text COLLATE pg_catalog."default",
    CONSTRAINT corsi_pkey PRIMARY KEY (id)
);
-- Script di inserimento corsi di esempio
-- Corsi di Informatica
INSERT INTO public.corsi (nome, docente, descrizione) VALUES
('Programmazione I', 'Prof. Mario Rossi', 'Corso base di programmazione in Java'),
('Programmazione II', 'Prof. Mario Rossi', 'Corso avanzato: strutture dati e algoritmi'),
('Basi di Dati', 'Prof.ssa Laura Bianchi', 'Progettazione e gestione di database relazionali'),
('Sviluppo Web', 'Prof. Giovanni Verdi', 'HTML, CSS, JavaScript e framework moderni'),
('Intelligenza Artificiale', 'Prof.ssa Anna Neri', 'Machine Learning e reti neurali'),
('Sistemi Operativi', 'Prof. Carlo Gialli', 'Architettura e gestione dei sistemi operativi');