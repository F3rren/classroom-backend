-- Ultimo script da eseguire
-- Table: public.notifiche
CREATE SEQUENCE IF NOT EXISTS notifiche_id_seq;

CREATE TABLE IF NOT EXISTS public.notifiche
(
    id bigint NOT NULL DEFAULT nextval('notifiche_id_seq'::regclass),
    admin_nome character varying(100) COLLATE pg_catalog."default",
    data_creazione timestamp(6) without time zone NOT NULL,
    data_lettura timestamp(6) without time zone,
    data_prenotazione timestamp(6) without time zone,
    letta boolean NOT NULL,
    messaggio character varying(1000) COLLATE pg_catalog."default" NOT NULL,
    nome_stanza character varying(100) COLLATE pg_catalog."default",
    prenotazione_id bigint,
    tipo character varying(50) COLLATE pg_catalog."default" NOT NULL,
    titolo character varying(200) COLLATE pg_catalog."default" NOT NULL,
    utente_id bigint NOT NULL,
    CONSTRAINT notifiche_pkey PRIMARY KEY (id),
    CONSTRAINT fk_notifiche_utente FOREIGN KEY (utente_id)
        REFERENCES public.utenti (id) MATCH SIMPLE
        ON UPDATE CASCADE
        ON DELETE CASCADE
);
