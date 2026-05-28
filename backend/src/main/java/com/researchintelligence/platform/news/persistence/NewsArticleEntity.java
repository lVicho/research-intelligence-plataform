package com.researchintelligence.platform.news.persistence;

import com.researchintelligence.platform.news.domain.NewsArticleStatus;
import com.researchintelligence.platform.shared.persistence.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "news_articles")
public class NewsArticleEntity extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String summary;

    @Column(nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NewsArticleStatus status = NewsArticleStatus.DRAFT;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "image_alt")
    private String imageAlt;

    @Column(name = "image_suggestion")
    private String imageSuggestion;

    @Column(name = "published_at")
    private Instant publishedAt;

    @ElementCollection
    @CollectionTable(name = "news_article_publications", joinColumns = @JoinColumn(name = "news_article_id"))
    @Column(name = "publication_id")
    private Set<Long> relatedPublicationIds = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "news_article_researchers", joinColumns = @JoinColumn(name = "news_article_id"))
    @Column(name = "researcher_id")
    private Set<Long> relatedResearcherIds = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "news_article_research_units", joinColumns = @JoinColumn(name = "news_article_id"))
    @Column(name = "research_unit_id")
    private Set<Long> relatedUnitIds = new LinkedHashSet<>();

    protected NewsArticleEntity() {
    }

    public NewsArticleEntity(String title, String summary, String body, NewsArticleStatus status) {
        this.title = title;
        this.summary = summary;
        this.body = body;
        this.status = status == null ? NewsArticleStatus.DRAFT : status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public NewsArticleStatus getStatus() {
        return status;
    }

    public void setStatus(NewsArticleStatus status) {
        this.status = status;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImageAlt() {
        return imageAlt;
    }

    public void setImageAlt(String imageAlt) {
        this.imageAlt = imageAlt;
    }

    public String getImageSuggestion() {
        return imageSuggestion;
    }

    public void setImageSuggestion(String imageSuggestion) {
        this.imageSuggestion = imageSuggestion;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Set<Long> getRelatedPublicationIds() {
        return relatedPublicationIds;
    }

    public Set<Long> getRelatedResearcherIds() {
        return relatedResearcherIds;
    }

    public Set<Long> getRelatedUnitIds() {
        return relatedUnitIds;
    }

    public void replaceRelatedPublicationIds(Set<Long> ids) {
        this.relatedPublicationIds.clear();
        this.relatedPublicationIds.addAll(ids);
    }

    public void replaceRelatedResearcherIds(Set<Long> ids) {
        this.relatedResearcherIds.clear();
        this.relatedResearcherIds.addAll(ids);
    }

    public void replaceRelatedUnitIds(Set<Long> ids) {
        this.relatedUnitIds.clear();
        this.relatedUnitIds.addAll(ids);
    }
}
