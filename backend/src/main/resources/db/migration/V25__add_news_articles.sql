create table news_articles (
    id bigserial primary key,
    title varchar(255) not null,
    summary text not null,
    body text not null,
    status varchar(30) not null,
    image_url varchar(1000),
    image_alt varchar(500),
    image_suggestion text,
    published_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by_user_id bigint references users(id),
    updated_by_user_id bigint references users(id),
    constraint chk_news_articles_status check (status in ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'ARCHIVED'))
);

create index idx_news_articles_status on news_articles(status);
create index idx_news_articles_published_at on news_articles(published_at desc);
create index idx_news_articles_created_at on news_articles(created_at desc);
create index idx_news_articles_updated_at on news_articles(updated_at desc);

create table news_article_publications (
    news_article_id bigint not null references news_articles(id) on delete cascade,
    publication_id bigint not null references publications(id),
    primary key (news_article_id, publication_id)
);

create index idx_news_article_publications_publication_id on news_article_publications(publication_id);

create table news_article_researchers (
    news_article_id bigint not null references news_articles(id) on delete cascade,
    researcher_id bigint not null references researchers(id),
    primary key (news_article_id, researcher_id)
);

create index idx_news_article_researchers_researcher_id on news_article_researchers(researcher_id);

create table news_article_research_units (
    news_article_id bigint not null references news_articles(id) on delete cascade,
    research_unit_id bigint not null references research_units(id),
    primary key (news_article_id, research_unit_id)
);

create index idx_news_article_research_units_research_unit_id on news_article_research_units(research_unit_id);
