import { AfterViewInit, Component, DestroyRef, ElementRef, Input, OnDestroy, ViewChild, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import cytoscape, { Core, ElementDefinition, EventObject, NodeSingular } from 'cytoscape';

import { GraphApiService, ResearcherGraphRequestOptions } from '../../core/api/graph-api.service';
import { GraphDensity, GraphNode, GraphNodeType, ResearchGraph } from '../../core/api/api-models';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { NavigationContextService } from '../../core/navigation/navigation-context.service';
import { VisibilityNoteComponent } from '../../shared/components/visibility-note.component';
import {
  graphNodeTypeLabel,
  publicationStatusLabel,
  publicationTypeLabel,
  researchUnitTypeLabel
} from '../../shared/utils/display-labels';
import { publicVisibilityNote, visibilityNoteForUser } from '../../shared/utils/visibility-labels';

interface GraphElementData {
  id: string;
  label?: string;
  type?: string;
}

interface MetadataEntry {
  key: string;
  value: string;
}

@Component({
  selector: 'rip-researcher-graph',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatCardModule, VisibilityNoteComponent],
  template: `
    <section class="graph-feature">
      <mat-card appearance="outlined" class="graph-controls-card">
        <mat-card-content>
          <rip-visibility-note [message]="graphVisibilityNote()" />

          <div class="graph-controls">
            <label class="density-control">
              <span>Densidad</span>
              <select [value]="density()" (change)="onDensityChange($event)">
                <option value="SIMPLE">Simple</option>
                <option value="NORMAL">Normal</option>
                <option value="COMPLETE">Completa</option>
              </select>
            </label>

            <div class="toggle-group" aria-label="Capas del grafo">
              <label><input type="checkbox" [checked]="includePublications()" (change)="setToggle('publications', $event)"> Publicaciones</label>
              <label><input type="checkbox" [checked]="includeTopics()" (change)="setToggle('topics', $event)"> Temas</label>
              <label><input type="checkbox" [checked]="includeCoauthors()" (change)="setToggle('coauthors', $event)"> Coautores</label>
              <label><input type="checkbox" [checked]="includeResearchUnits()" (change)="setToggle('researchUnits', $event)"> Unidades</label>
              <label><input type="checkbox" [checked]="includeExternalAuthors()" (change)="setToggle('externalAuthors', $event)"> Autores externos</label>
            </div>
          </div>
        </mat-card-content>
      </mat-card>

      @if (graph()?.warnings?.length) {
        <div class="warning-list">
          @for (warning of graph()!.warnings; track warning) {
            <span>{{ warning }}</span>
          }
        </div>
      }

      <section class="graph-layout">
        <mat-card appearance="outlined" class="graph-card">
          <mat-card-content>
            <div class="graph-surface" #graphContainer>
              @if (loading()) {
                <div class="graph-state">Cargando grafo...</div>
              } @else if (errorMessage()) {
                <div class="graph-state error">{{ errorMessage() }}</div>
              } @else if ((graph()?.nodes?.length ?? 0) === 0) {
                <div class="graph-state">No hay datos suficientes para dibujar el grafo.</div>
              }
            </div>

            <div class="legend" aria-label="Leyenda del grafo">
              <span><i class="legend-dot researcher"></i> Investigador</span>
              <span><i class="legend-dot unit"></i> Unidad</span>
              <span><i class="legend-dot publication"></i> Publicación</span>
              <span><i class="legend-dot topic"></i> Tema</span>
              <span><i class="legend-dot coauthor"></i> Coautor</span>
              <span><i class="legend-dot external"></i> Autor externo</span>
            </div>
          </mat-card-content>
        </mat-card>

        <mat-card appearance="outlined" class="detail-panel">
          <mat-card-header>
            <mat-card-title>{{ selectedNode()?.label || 'Resumen del grafo' }}</mat-card-title>
            <mat-card-subtitle>{{ selectedNode() ? nodeTypeLabel(selectedNode()!) : graphSummaryLabel() }}</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            @if (selectedNode(); as node) {
              <div class="node-summary">
                <span class="node-type-pill">{{ nodeTypeLabel(node) }}</span>
                <strong>{{ relatedCountLabel(node) }}</strong>
              </div>

              @if (nodeDetailRoute(node); as route) {
                <a mat-stroked-button [routerLink]="route" [queryParams]="navigationContext.returnQueryParams('Volver al investigador')">Ver detalle</a>
              }

              <dl>
                @for (entry of selectedMetadata(); track entry.key) {
                  <div>
                    <dt>{{ entry.key }}</dt>
                    <dd>{{ entry.value }}</dd>
                  </div>
                }
              </dl>
            } @else {
              <div class="graph-summary">
                <div>
                  <span>Nodos mostrados</span>
                  <strong>{{ graph()?.metadata?.displayedNodes ?? 0 }}</strong>
                </div>
                <div>
                  <span>Relaciones mostradas</span>
                  <strong>{{ graph()?.metadata?.displayedEdges ?? 0 }}</strong>
                </div>
                <div>
                  <span>Total disponible</span>
                  <strong>{{ graph()?.metadata?.totalNodes ?? 0 }} nodos</strong>
                </div>
              </div>
              <p class="empty">Haz clic en un nodo para revisar sus metadatos, conexiones y enlace de detalle.</p>
            }
          </mat-card-content>
        </mat-card>
      </section>
    </section>
  `,
  styles: [`
    .graph-feature {
      display: grid;
      gap: 18px;
    }

    mat-card-content {
      padding: 16px;
    }

    .graph-controls {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 18px;
      flex-wrap: wrap;
    }

    .density-control {
      display: grid;
      gap: 6px;
      color: #324155;
      font-size: 0.86rem;
      font-weight: 760;
    }

    .density-control select {
      min-width: 150px;
      padding: 8px 10px;
      border: 1px solid #cfd9e4;
      border-radius: 8px;
      background: #ffffff;
      color: #233044;
      font: inherit;
      font-weight: 600;
    }

    .toggle-group {
      display: flex;
      flex-wrap: wrap;
      gap: 10px 14px;
    }

    .toggle-group label {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      color: #324155;
      font-size: 0.9rem;
      line-height: 1.2;
      white-space: nowrap;
    }

    .toggle-group input {
      width: 16px;
      height: 16px;
      accent-color: #297c9d;
    }

    .warning-list {
      display: grid;
      gap: 8px;
    }

    .warning-list span {
      padding: 10px 12px;
      border: 1px solid #efd18b;
      border-radius: 8px;
      background: #fff9e9;
      color: #72510d;
      font-size: 0.9rem;
      line-height: 1.35;
    }

    .graph-layout {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(300px, 360px);
      gap: 24px;
      align-items: start;
    }

    .graph-card {
      min-width: 0;
    }

    .graph-surface {
      position: relative;
      min-height: 720px;
      border: 1px solid #d9e0e8;
      border-radius: 8px;
      background: #ffffff;
      overflow: hidden;
    }

    .graph-state {
      position: absolute;
      inset: 0;
      display: grid;
      place-items: center;
      padding: 24px;
      color: #667487;
      text-align: center;
      background: rgba(255, 255, 255, 0.82);
      z-index: 2;
    }

    .graph-state.error {
      color: #8a1f17;
    }

    .legend {
      display: flex;
      flex-wrap: wrap;
      gap: 10px 16px;
      margin-top: 14px;
      color: #4c5d70;
      font-size: 0.86rem;
    }

    .legend span {
      display: inline-flex;
      align-items: center;
      gap: 7px;
    }

    .legend-dot {
      width: 11px;
      height: 11px;
      border-radius: 999px;
      display: inline-block;
    }

    .legend-dot.researcher {
      background: #2563eb;
    }

    .legend-dot.unit {
      background: #0f766e;
    }

    .legend-dot.publication {
      background: #7c3aed;
    }

    .legend-dot.topic {
      background: #d97706;
    }

    .legend-dot.coauthor {
      background: #60a5fa;
    }

    .legend-dot.external {
      background: #64748b;
    }

    .detail-panel {
      align-self: start;
      position: sticky;
      top: 18px;
    }

    mat-card-title {
      font-size: 1rem;
      line-height: 1.3;
      overflow-wrap: anywhere;
    }

    .node-summary {
      display: grid;
      gap: 8px;
      margin-bottom: 14px;
    }

    .node-type-pill {
      width: fit-content;
      padding: 5px 9px;
      border: 1px solid #d7e0ea;
      border-radius: 999px;
      background: #f8fafc;
      color: #324155;
      font-size: 0.8rem;
      font-weight: 760;
    }

    .graph-summary {
      display: grid;
      gap: 10px;
      margin-bottom: 14px;
    }

    .graph-summary div {
      display: grid;
      gap: 4px;
      padding: 11px 12px;
      border: 1px solid #e0e7ee;
      border-radius: 8px;
      background: #fbfcfe;
    }

    .graph-summary span {
      color: #667487;
      font-size: 0.74rem;
      font-weight: 760;
      text-transform: uppercase;
    }

    .graph-summary strong {
      color: #233044;
      font-size: 0.94rem;
    }

    dl {
      display: grid;
      gap: 10px;
      margin: 16px 0 0;
    }

    dt {
      color: #5a6677;
      font-size: 0.76rem;
      font-weight: 760;
      text-transform: uppercase;
    }

    dd {
      margin: 2px 0 0;
      color: #233044;
      line-height: 1.35;
      overflow-wrap: anywhere;
    }

    .empty {
      margin: 0;
      color: #667487;
      line-height: 1.45;
    }

    @media (max-width: 1100px) {
      .graph-layout {
        grid-template-columns: 1fr;
      }

      .detail-panel {
        position: static;
      }
    }

    @media (max-width: 640px) {
      .graph-controls {
        align-items: stretch;
        flex-direction: column;
      }

      .density-control select {
        width: 100%;
      }

      .toggle-group {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }

      .toggle-group label {
        white-space: normal;
      }

      .graph-surface {
        min-height: 560px;
      }
    }
  `]
})
export class ResearcherGraphComponent implements AfterViewInit, OnDestroy {
  private readonly api = inject(GraphApiService);
  private readonly auth = inject(AuthStateService);
  private readonly destroyRef = inject(DestroyRef);
  readonly navigationContext = inject(NavigationContextService);
  private cytoscapeInstance: Core | null = null;
  private viewReady = false;

  @ViewChild('graphContainer')
  private graphContainer?: ElementRef<HTMLElement>;

  @Input({ required: true })
  set researcherId(value: number) {
    this.researcherIdValue = value;
    this.loadGraph();
  }

  @Input()
  portalView = false;

  private researcherIdValue: number | null = null;
  readonly graph = signal<ResearchGraph | null>(null);
  readonly selectedNode = signal<GraphNode | null>(null);
  readonly selectedMetadata = signal<MetadataEntry[]>([]);
  readonly loading = signal(false);
  readonly errorMessage = signal('');
  readonly graphVisibilityNote = computed(() => this.portalView ? publicVisibilityNote() : visibilityNoteForUser(this.auth.currentUser()));

  readonly density = signal<GraphDensity>('NORMAL');
  readonly includePublications = signal(true);
  readonly includeTopics = signal(true);
  readonly includeCoauthors = signal(true);
  readonly includeResearchUnits = signal(true);
  readonly includeExternalAuthors = signal(false);

  ngAfterViewInit(): void {
    this.viewReady = true;
    this.loadGraph();
  }

  ngOnDestroy(): void {
    this.cytoscapeInstance?.destroy();
  }

  onDensityChange(event: Event): void {
    const value = (event.target as HTMLSelectElement).value as GraphDensity;
    this.density.set(value);
    this.loadGraph();
  }

  setToggle(kind: 'publications' | 'topics' | 'coauthors' | 'researchUnits' | 'externalAuthors', event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    switch (kind) {
      case 'publications':
        this.includePublications.set(checked);
        break;
      case 'topics':
        this.includeTopics.set(checked);
        break;
      case 'coauthors':
        this.includeCoauthors.set(checked);
        break;
      case 'researchUnits':
        this.includeResearchUnits.set(checked);
        break;
      case 'externalAuthors':
        this.includeExternalAuthors.set(checked);
        break;
    }
    this.loadGraph();
  }

  nodeTypeLabel(node: GraphNode): string {
    if (node.type === 'researcher' && node.id !== this.mainResearcherNodeId()) {
      return 'Coautor';
    }
    return graphNodeTypeLabel(node.type);
  }

  graphSummaryLabel(): string {
    const metadata = this.graph()?.metadata;
    if (!metadata) {
      return 'Selecciona un nodo';
    }
    return `${metadata.displayedNodes} nodos y ${metadata.displayedEdges} relaciones`;
  }

  relatedCountLabel(node: GraphNode): string {
    const count = this.graph()?.edges.filter((edge) => edge.source === node.id || edge.target === node.id).length ?? 0;
    return count === 1 ? '1 relación directa' : `${count} relaciones directas`;
  }

  nodeDetailRoute(node: GraphNode): Array<string | number> | null {
    const id = this.numericMetadataId(node);
    if (id === null) {
      return null;
    }
    if (this.portalView) {
      return node.type === 'publication' ? ['/publications', id] : null;
    }
    switch (node.type) {
      case 'researcher':
        return ['/researchers', id];
      case 'publication':
        return ['/publications', id];
      case 'research_unit':
        return ['/research-units', id];
      default:
        return null;
    }
  }

  private loadGraph(): void {
    if (!this.viewReady || this.researcherIdValue === null) {
      return;
    }
    this.loading.set(true);
    this.errorMessage.set('');
    this.api.researcherGraph(this.researcherIdValue, this.requestOptions())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (graph) => {
          this.graph.set(graph);
          this.selectedNode.set(null);
          this.selectedMetadata.set([]);
          this.loading.set(false);
          requestAnimationFrame(() => this.renderGraph(graph));
        },
        error: () => {
          this.loading.set(false);
          this.errorMessage.set('No se pudo cargar el grafo del investigador.');
          this.graph.set(null);
          this.selectedNode.set(null);
          this.selectedMetadata.set([]);
          this.cytoscapeInstance?.destroy();
          this.cytoscapeInstance = null;
        }
      });
  }

  private requestOptions(): ResearcherGraphRequestOptions {
    return {
      density: this.density(),
      includePublications: this.includePublications(),
      includeTopics: this.includeTopics(),
      includeCoauthors: this.includeCoauthors(),
      includeResearchUnits: this.includeResearchUnits(),
      includeExternalAuthors: this.includeExternalAuthors()
    };
  }

  private renderGraph(graph: ResearchGraph): void {
    const container = this.graphContainer?.nativeElement;
    if (!container) {
      return;
    }
    this.cytoscapeInstance?.destroy();
    this.cytoscapeInstance = cytoscape({
      container,
      elements: this.toElements(graph),
      minZoom: 0.25,
      maxZoom: 2.5,
      wheelSensitivity: 0.18,
      style: [
        {
          selector: 'node',
          style: {
            'background-color': 'data(color)',
            'border-color': '#ffffff',
            'border-width': 2,
            'color': '#1f2937',
            'font-size': 11,
            'label': 'data(visibleLabel)',
            'text-background-color': '#ffffff',
            'text-background-opacity': 0.9,
            'text-background-padding': '3px',
            'text-max-width': '135px',
            'text-margin-y': 6,
            'text-valign': 'bottom',
            'text-wrap': 'wrap',
            'width': 'data(size)',
            'height': 'data(size)'
          }
        },
        {
          selector: 'node.show-label',
          style: {
            'label': 'data(label)'
          }
        },
        {
          selector: 'edge',
          style: {
            'curve-style': 'bezier',
            'line-color': '#a6b4c3',
            'target-arrow-color': '#a6b4c3',
            'target-arrow-shape': 'triangle',
            'width': 'data(width)'
          }
        },
        {
          selector: 'node:selected',
          style: {
            'border-color': '#111827',
            'border-width': 4
          }
        }
      ],
      layout: {
        name: 'cose',
        animate: false,
        fit: true,
        padding: 72,
        nodeRepulsion: this.layoutNodeRepulsion(),
        idealEdgeLength: this.layoutEdgeLength(),
        componentSpacing: 150,
        nodeOverlap: 28,
        nestingFactor: 1.2,
        gravity: 0.22,
        numIter: 1800
      }
    });

    this.cytoscapeInstance.on('tap', 'node', (event: EventObject) => {
      const data = event.target.data() as GraphElementData;
      const node = graph.nodes.find((candidate) => candidate.id === data.id) ?? null;
      this.selectedNode.set(node);
      this.selectedMetadata.set(node ? this.metadataEntries(node) : []);
      this.cytoscapeInstance?.nodes().removeClass('show-label');
      event.target.addClass('show-label');
    });
    this.cytoscapeInstance.on('mouseover', 'node', (event: EventObject) => {
      event.target.addClass('show-label');
    });
    this.cytoscapeInstance.on('mouseout', 'node', (event: EventObject) => {
      const node = event.target as NodeSingular;
      if (!node.selected()) {
        node.removeClass('show-label');
      }
    });
    this.cytoscapeInstance.on('tap', (event: EventObject) => {
      if (event.target === this.cytoscapeInstance) {
        this.selectedNode.set(null);
        this.selectedMetadata.set([]);
        this.cytoscapeInstance?.nodes().removeClass('show-label');
      }
    });
    requestAnimationFrame(() => {
      this.cytoscapeInstance?.resize();
      this.cytoscapeInstance?.fit(undefined, 72);
    });
  }

  private toElements(graph: ResearchGraph): ElementDefinition[] {
    return [
      ...graph.nodes.map((node) => ({
        data: {
          id: node.id,
          label: node.label,
          visibleLabel: this.initialVisibleLabel(node, graph),
          type: node.type,
          color: this.nodeColor(node),
          size: this.nodeSize(node)
        }
      })),
      ...graph.edges.map((edge) => ({
        data: {
          id: edge.id,
          source: edge.source,
          target: edge.target,
          type: edge.type,
          weight: edge.weight,
          width: Math.max(1, Math.min(edge.weight, 5))
        }
      }))
    ];
  }

  private initialVisibleLabel(node: GraphNode, graph: ResearchGraph): string {
    if (node.id === this.mainResearcherNodeId()) {
      return node.label;
    }
    if (graph.nodes.length <= 18 && (node.type === 'research_unit' || node.type === 'publication')) {
      return node.label;
    }
    return '';
  }

  private metadataEntries(node: GraphNode): MetadataEntry[] {
    return Object.entries(node.metadata)
      .filter(([key]) => key !== 'id')
      .map(([key, value]) => ({
        key: this.metadataLabel(key),
        value: this.metadataValue(key, value)
      }));
  }

  private metadataLabel(key: string): string {
    const labels: Record<string, string> = {
      email: 'Correo',
      orcid: 'ORCID',
      active: 'Activo',
      shortName: 'Nombre corto',
      unitType: 'Tipo de unidad',
      city: 'Ciudad',
      country: 'País',
      year: 'Año',
      type: 'Tipo',
      status: 'Estado',
      doi: 'DOI',
      normalizedName: 'Nombre normalizado',
      externalAffiliation: 'Afiliación externa'
    };
    return labels[key] ?? key;
  }

  private metadataValue(key: string, value: unknown): string {
    if (typeof value === 'boolean') {
      return value ? 'Sí' : 'No';
    }
    if (key === 'type' && typeof value === 'string') {
      return publicationTypeLabel(value);
    }
    if (key === 'status' && typeof value === 'string') {
      return publicationStatusLabel(value);
    }
    if (key === 'unitType' && typeof value === 'string') {
      return researchUnitTypeLabel(value);
    }
    return String(value);
  }

  private nodeColor(node: GraphNode): string {
    switch (node.type) {
      case 'researcher':
        return node.id === this.mainResearcherNodeId() ? '#2563eb' : '#60a5fa';
      case 'research_unit':
        return '#0f766e';
      case 'publication':
        return '#7c3aed';
      case 'topic':
        return '#d97706';
      case 'external_author':
        return '#64748b';
    }
  }

  private nodeSize(node: GraphNode): number {
    if (node.id === this.mainResearcherNodeId()) {
      return 54;
    }
    return node.type === 'publication' ? 38 : node.type === 'researcher' ? 34 : 30;
  }

  private layoutNodeRepulsion(): number {
    switch (this.density()) {
      case 'SIMPLE':
        return 14000;
      case 'COMPLETE':
        return 23000;
      case 'NORMAL':
        return 18000;
    }
  }

  private layoutEdgeLength(): number {
    switch (this.density()) {
      case 'SIMPLE':
        return 155;
      case 'COMPLETE':
        return 210;
      case 'NORMAL':
        return 180;
    }
  }

  private mainResearcherNodeId(): string {
    return this.researcherIdValue === null ? '' : `researcher:${this.researcherIdValue}`;
  }

  private numericMetadataId(node: GraphNode): number | null {
    const value = node.metadata['id'];
    return typeof value === 'number' ? value : null;
  }
}
