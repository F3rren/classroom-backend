-- Quarto script da eseguire
-- Table: public.prenotazioni
CREATE SEQUENCE IF NOT EXISTS prenotazioni_id_seq;

CREATE TABLE IF NOT EXISTS public.prenotazioni
(
    id bigint NOT NULL DEFAULT nextval('prenotazioni_id_seq'::regclass),
    aula_id bigint NOT NULL,
    corso_id bigint,
    utente_id bigint NOT NULL,
    inizio timestamp without time zone NOT NULL,
    fine timestamp without time zone NOT NULL,
    stato character varying(20) COLLATE pg_catalog."default" NOT NULL DEFAULT 'prenotata'::character varying,
    descrizione text COLLATE pg_catalog."default",
    data_creazione timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT prenotazione_pkey PRIMARY KEY (id),
    CONSTRAINT prenotazione_aula_id_fkey FOREIGN KEY (aula_id)
        REFERENCES public.aule (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE,
    CONSTRAINT prenotazione_corso_id_fkey FOREIGN KEY (corso_id)
        REFERENCES public.corsi (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE SET NULL,
    CONSTRAINT prenotazione_utente_id_fkey FOREIGN KEY (utente_id)
        REFERENCES public.utenti (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE CASCADE,
    CONSTRAINT prenotazione_stato_check CHECK (stato::text = ANY (ARRAY['prenotata'::character varying, 'confermata'::character varying, 'bloccata'::character varying, 'manutenzione'::character varying, 'annullata'::character varying]::text[])),
    CONSTRAINT chk_orario_valido CHECK (fine > inizio)
);
