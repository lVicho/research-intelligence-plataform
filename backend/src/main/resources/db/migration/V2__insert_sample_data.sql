insert into research_units (id, name, short_name, type, parent_id, country, city, website, active, created_at, updated_at) values
    (1, 'Northbridge University', 'NBU', 'UNIVERSITY', null, 'United States', 'Boston', 'https://northbridge.example.edu', true, now(), now()),
    (2, 'Faculty of Health Sciences', 'FHS', 'FACULTY', 1, 'United States', 'Boston', 'https://northbridge.example.edu/health', true, now(), now()),
    (3, 'Department of Biomedical Informatics', 'DBMI', 'DEPARTMENT', 2, 'United States', 'Boston', 'https://northbridge.example.edu/dbmi', true, now(), now()),
    (4, 'Center for Clinical AI', 'CCAI', 'CENTER', 2, 'United States', 'Boston', 'https://northbridge.example.edu/ccai', true, now(), now()),
    (5, 'Riverbend Medical Research Hospital', 'RMRH', 'HOSPITAL', null, 'United States', 'Cambridge', 'https://riverbend.example.org', true, now(), now()),
    (6, 'Translational Genomics Lab', 'TGL', 'LAB', 5, 'United States', 'Cambridge', 'https://riverbend.example.org/genomics', true, now(), now()),
    (7, 'Atlantic Institute for Sustainable Systems', 'AISS', 'INSTITUTE', null, 'Portugal', 'Lisbon', 'https://aiss.example.pt', true, now(), now()),
    (8, 'Urban Climate Research Group', 'UCRG', 'RESEARCH_GROUP', 7, 'Portugal', 'Lisbon', 'https://aiss.example.pt/urban-climate', true, now(), now());

insert into researchers (id, full_name, display_name, email, orcid, active, created_at, updated_at) values
    (1, 'Maya Chen', 'Maya Chen', 'maya.chen@northbridge.example.edu', '0000-0002-1825-0097', true, now(), now()),
    (2, 'Omar Alvarez', 'Omar Alvarez', 'omar.alvarez@riverbend.example.org', '0000-0003-2145-8910', true, now(), now()),
    (3, 'Priya Raman', 'Priya Raman', 'priya.raman@northbridge.example.edu', '0000-0001-8123-4550', true, now(), now()),
    (4, 'Elena Kovacs', 'Elena Kovacs', 'elena.kovacs@aiss.example.pt', '0000-0002-9012-7711', true, now(), now()),
    (5, 'Jonas Weber', 'Jonas Weber', 'jonas.weber@northbridge.example.edu', null, true, now(), now()),
    (6, 'Sara Williams', 'Sara Williams', 'sara.williams@riverbend.example.org', '0000-0001-4455-3312', false, now(), now());

insert into researcher_affiliations (id, researcher_id, research_unit_id, role, affiliation_type, start_date, end_date, primary_affiliation, created_at, updated_at) values
    (1, 1, 3, 'Associate Professor', 'MEMBER', '2019-09-01', null, true, now(), now()),
    (2, 1, 4, 'Clinical AI Lead', 'LEADER', '2021-01-15', null, false, now(), now()),
    (3, 2, 5, 'Principal Investigator', 'MEMBER', '2018-03-01', null, true, now(), now()),
    (4, 2, 6, 'Genomics Program Lead', 'LEADER', '2020-05-01', null, false, now(), now()),
    (5, 3, 4, 'Research Scientist', 'MEMBER', '2022-02-01', null, true, now(), now()),
    (6, 3, 3, 'Adjunct Lecturer', 'COLLABORATOR', '2022-09-01', null, false, now(), now()),
    (7, 4, 8, 'Group Coordinator', 'LEADER', '2017-06-01', null, true, now(), now()),
    (8, 5, 3, 'Doctoral Researcher', 'MEMBER', '2023-09-01', null, true, now(), now()),
    (9, 5, 5, 'Clinical Collaborator', 'VISITING', '2024-01-01', null, false, now(), now()),
    (10, 6, 6, 'Former Data Curator', 'FORMER_MEMBER', '2018-01-01', '2023-12-31', false, now(), now());

insert into publications (id, title, abstract_text, year, type, status, doi, source, url, created_at, updated_at) values
    (1, 'Clinical Foundation Models for Multimodal Patient Risk Review', 'A local-first evaluation of multimodal clinical models for structured and unstructured patient data.', 2025, 'ARTICLE', 'PUBLISHED', '10.1000/rip.2025.001', 'Journal of Clinical AI Systems', 'https://doi.org/10.1000/rip.2025.001', now(), now()),
    (2, 'Graph-Based Cohort Discovery in Translational Genomics', 'A graph-oriented approach to cohort discovery across genomic and clinical research records.', 2024, 'CONFERENCE_PAPER', 'PUBLISHED', '10.1000/rip.2024.017', 'International Conference on Biomedical Data', 'https://doi.org/10.1000/rip.2024.017', now(), now()),
    (3, 'Urban Heat Exposure and Hospital Admission Patterns', 'Analysis of urban climate exposure signals and downstream hospital admission trends.', 2023, 'REPORT', 'PUBLISHED', null, 'Atlantic Institute Working Papers', 'https://aiss.example.pt/reports/urban-heat-health', now(), now()),
    (4, 'Reusable Data Stewardship Workflows for Research Hospitals', 'A practical workflow model for curating research datasets in hospital-led studies.', 2024, 'ARTICLE', 'ACCEPTED', '10.1000/rip.2024.044', 'Data Stewardship Review', 'https://doi.org/10.1000/rip.2024.044', now(), now()),
    (5, 'Federated Topic Mapping for Institutional Research Portfolios', 'Topic normalization and portfolio exploration for multi-unit research organizations.', 2025, 'SOFTWARE', 'IN_PRESS', null, 'Open Research Tools', 'https://northbridge.example.edu/tools/topic-mapping', now(), now());

insert into publication_authors (publication_id, researcher_id, external_author_name, external_affiliation, author_order, corresponding_author) values
    (1, 1, null, null, 1, true),
    (1, 3, null, null, 2, false),
    (1, null, 'Grace Patel', 'Harborview Health Analytics', 3, false),
    (2, 2, null, null, 1, true),
    (2, 5, null, null, 2, false),
    (2, null, 'Nina Costa', 'European Genome Archive', 3, false),
    (3, 4, null, null, 1, true),
    (3, null, 'Rui Martins', 'Lisbon Public Health Observatory', 2, false),
    (4, 2, null, null, 1, false),
    (4, 6, null, null, 2, true),
    (4, null, 'Thomas Greene', 'Open Data Commons', 3, false),
    (5, 1, null, null, 1, true),
    (5, 4, null, null, 2, false),
    (5, 5, null, null, 3, false);

insert into topics (id, name, normalized_name) values
    (1, 'Clinical AI', 'clinical ai'),
    (2, 'Multimodal Data', 'multimodal data'),
    (3, 'Genomics', 'genomics'),
    (4, 'Knowledge Graphs', 'knowledge graphs'),
    (5, 'Urban Climate', 'urban climate'),
    (6, 'Public Health', 'public health'),
    (7, 'Data Stewardship', 'data stewardship'),
    (8, 'Research Analytics', 'research analytics'),
    (9, 'Topic Mapping', 'topic mapping');

insert into publication_topics (publication_id, topic_id) values
    (1, 1),
    (1, 2),
    (2, 3),
    (2, 4),
    (3, 5),
    (3, 6),
    (4, 7),
    (4, 6),
    (5, 8),
    (5, 9),
    (5, 4);

select setval('research_units_id_seq', (select max(id) from research_units));
select setval('researchers_id_seq', (select max(id) from researchers));
select setval('researcher_affiliations_id_seq', (select max(id) from researcher_affiliations));
select setval('publications_id_seq', (select max(id) from publications));
select setval('topics_id_seq', (select max(id) from topics));
