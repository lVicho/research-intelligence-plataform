create extension if not exists vector;

create table publication_embeddings (
    publication_id bigint primary key references publications(id) on delete cascade,
    provider varchar(50) not null,
    model varchar(120) not null,
    dimension integer not null,
    embedding vector not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint ck_publication_embeddings_dimension check (dimension > 0)
);

create index idx_publication_embeddings_provider_model_dimension
    on publication_embeddings(provider, model, dimension);
