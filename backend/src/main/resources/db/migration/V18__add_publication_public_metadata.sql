alter table publications
    add column public_summary text,
    add column publication_date date,
    add column source_detail text;

create index idx_publications_publication_date on publications(publication_date);
