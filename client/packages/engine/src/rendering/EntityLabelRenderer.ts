/**
 * EntityLabelRenderer - Renders labels with name and health bars above entities
 *
 * Creates billboard labels that show:
 * - Entity name (always)
 * - Health bar with percentage (if health/healthMax are defined)
 *
 * Labels use BILLBOARDMODE_Y (same as items) and render in SELECTION_OVERLAY group.
 */

import { Vector3, Mesh, MeshBuilder, StandardMaterial, Scene } from '@babylonjs/core';
import { AdvancedDynamicTexture, TextBlock, Rectangle, Control, StackPanel } from '@babylonjs/gui';
import { getLogger, type ClientEntity } from '@nimbus/shared';
import { RENDERING_GROUPS } from '../config/renderingGroups';

const logger = getLogger('EntityLabelRenderer');

/**
 * Format number to human-readable string
 * Examples: 1500 -> '1.5K', 4500000 -> '4.5M'
 */
function formatNumber(value: number): string {
  if (value >= 1000000) {
    return `${(value / 1000000).toFixed(1)}M`;
  } else if (value >= 1000) {
    return `${(value / 1000).toFixed(1)}K`;
  } else {
    return Math.floor(value).toString();
  }
}

/**
 * Label data for a single entity
 */
interface EntityLabel {
  entityId: string;
  planeMesh: Mesh;
  guiTexture: AdvancedDynamicTexture;
  nameTextBlock: TextBlock;
  healthBarContainer?: Rectangle;
  healthFill?: Rectangle;
  healthText?: TextBlock;
}

/**
 * EntityLabelRenderer - Manages billboard labels for entities
 */
export class EntityLabelRenderer {
  private scene: Scene;
  private entityLabels: Map<string, EntityLabel> = new Map();

  constructor(scene: Scene) {
    this.scene = scene;
    logger.debug('EntityLabelRenderer initialized');
  }

  /**
   * Create label for entity
   *
   * Always shows name, optionally shows health bar if health values exist.
   *
   * @param clientEntity Entity to create label for
   */
  createLabel(clientEntity: ClientEntity): void {
    // Don't create duplicate labels
    if (this.entityLabels.has(clientEntity.id)) {
      return;
    }

    const entity = clientEntity.entity;
    const hasHealth = entity.health !== undefined && entity.healthMax !== undefined && entity.health < entity.healthMax;

    // Create plane mesh for the label
    const labelPlane = MeshBuilder.CreatePlane(
      `entity_label_${clientEntity.id}`,
      { width: 1.0, height: hasHealth ? 0.4 : 0.2 },
      this.scene
    );

    // Position label above entity
    const labelPos = this.calculateLabelPosition(clientEntity);
    labelPlane.position.copyFrom(labelPos);
    labelPlane.billboardMode = Mesh.BILLBOARDMODE_Y; // Same as item billboard
    labelPlane.isPickable = false;

    // Render on top of world geometry
    labelPlane.renderingGroupId = RENDERING_GROUPS.SELECTION_OVERLAY;
    labelPlane.alwaysSelectAsActiveMesh = true;

    // Create transparent material
    const material = new StandardMaterial(`entity_label_mat_${clientEntity.id}`, this.scene);
    material.disableDepthWrite = true; // Don't write to depth buffer
    material.disableColorWrite = true; // Don't write color (GUI texture will)
    labelPlane.material = material;

    // Create GUI texture (high resolution for crisp text)
    const guiTexture = AdvancedDynamicTexture.CreateForMesh(labelPlane, 512, hasHealth ? 256 : 128);

    // Create stack panel (vertical layout, top to bottom)
    const stack = new StackPanel();
    stack.width = "100%";
    stack.height = "100%";
    stack.isVertical = true;
    stack.verticalAlignment = Control.VERTICAL_ALIGNMENT_TOP;
    guiTexture.addControl(stack);

    // Name text block (always shown)
    const nameText = new TextBlock();
    nameText.text = entity.name || clientEntity.id;
    nameText.color = "white";
    nameText.fontSize = hasHealth ? 36 : 40;
    nameText.fontWeight = "bold";
    nameText.height = hasHealth ? "70%" : "100%";
    nameText.textHorizontalAlignment = Control.HORIZONTAL_ALIGNMENT_CENTER;
    nameText.textVerticalAlignment = Control.VERTICAL_ALIGNMENT_CENTER;
    nameText.outlineWidth = 4;
    nameText.outlineColor = "black";
    stack.addControl(nameText);

    logger.info('Name text created', {
      entityId: clientEntity.id,
      name: entity.name || clientEntity.id,
      fontSize: nameText.fontSize,
    });

    const label: EntityLabel = {
      entityId: clientEntity.id,
      planeMesh: labelPlane,
      guiTexture,
      nameTextBlock: nameText,
    };

    // Add health bar if health values exist
    if (hasHealth) {
      this.createHealthBar(stack, label, entity.health!, entity.healthMax!);
    }

    // Store label
    this.entityLabels.set(clientEntity.id, label);

    logger.debug('Entity label created', {
      entityId: clientEntity.id,
      name: entity.name,
      hasHealth,
    });
  }

  /**
   * Create health bar UI elements
   *
   * @param stack Parent stack panel
   * @param label Label data to store references
   * @param health Current health
   * @param healthMax Maximum health
   */
  private createHealthBar(
    stack: StackPanel,
    label: EntityLabel,
    health: number,
    healthMax: number
  ): void {
    // Health bar background container (30% height, half of previous 50%)
    const healthBarBg = new Rectangle();
    healthBarBg.height = "30%";
    healthBarBg.width = "100%";
    healthBarBg.thickness = 2;
    healthBarBg.color = "rgba(255, 255, 255, 0.8)";
    healthBarBg.background = "rgba(0, 0, 0, 0.7)"; // Black background
    healthBarBg.cornerRadius = 4;
    stack.addControl(healthBarBg);

    // Health fill (inside container)
    const healthFill = new Rectangle();
    healthFill.horizontalAlignment = Control.HORIZONTAL_ALIGNMENT_LEFT;
    healthFill.thickness = 0;
    healthFill.cornerRadius = 4;
    healthBarBg.addControl(healthFill);

    // Absolute health text (over health bar)
    const healthText = new TextBlock();
    healthText.color = "white";
    healthText.fontSize = 28;
    healthText.fontWeight = "bold";
    healthText.outlineWidth = 3;
    healthText.outlineColor = "black";
    healthBarBg.addControl(healthText);

    // Store references
    label.healthBarContainer = healthBarBg;
    label.healthFill = healthFill;
    label.healthText = healthText;

    // Update initial health display
    this.updateHealthBar(label, health, healthMax);
  }

  /**
   * Update health bar display
   *
   * @param label Label to update
   * @param health Current health
   * @param healthMax Maximum health
   */
  private updateHealthBar(label: EntityLabel, health: number, healthMax: number): void {
    if (!label.healthFill || !label.healthText) {
      return;
    }

    const percent = (health / healthMax) * 100;

    // Update fill width
    label.healthFill.width = `${percent}%`;

    // Update text with current health value only (human-readable)
    label.healthText.text = formatNumber(health);

    // Light dark red color (always)
    label.healthFill.background = "rgba(139, 0, 0, 0.9)"; // Light dark red (DarkRed with transparency)
  }

  /**
   * Update label for entity
   *
   * Updates position, name, and health bar.
   *
   * @param clientEntity Entity to update label for
   */
  updateLabel(clientEntity: ClientEntity): void {
    const entity = clientEntity.entity;
    const shouldShowHealthBar = entity.health !== undefined && entity.healthMax !== undefined && entity.health < entity.healthMax;

    const label = this.entityLabels.get(clientEntity.id);

    if (!label) {
      // Label doesn't exist yet, create it
      this.createLabel(clientEntity);
      return;
    }

    // Check if health bar status changed (need to recreate label with different layout)
    const currentHasHealthBar = label.healthBarContainer !== undefined;
    if (currentHasHealthBar !== shouldShowHealthBar) {
      // Health bar status changed - recreate label
      this.removeLabel(clientEntity.id);
      this.createLabel(clientEntity);
      return;
    }

    // Update position
    const labelPos = this.calculateLabelPosition(clientEntity);
    label.planeMesh.position.copyFrom(labelPos);

    // Update name (in case it changed)
    label.nameTextBlock.text = entity.name || clientEntity.id;

    // Update health bar if it exists and values are defined
    if (label.healthFill && label.healthText && entity.health !== undefined && entity.healthMax !== undefined) {
      this.updateHealthBar(label, entity.health, entity.healthMax);
    }
  }

  /**
   * Remove label for entity
   *
   * @param entityId Entity ID
   */
  removeLabel(entityId: string): void {
    const label = this.entityLabels.get(entityId);
    if (!label) {
      return;
    }

    // Dispose resources
    label.planeMesh.dispose();
    label.guiTexture.dispose();

    // Remove from map
    this.entityLabels.delete(entityId);

    logger.debug('Entity label removed', { entityId });
  }

  /**
   * Set label visibility
   *
   * @param entityId Entity ID
   * @param visible Visibility flag
   */
  setLabelVisibility(entityId: string, visible: boolean): void {
    const label = this.entityLabels.get(entityId);
    if (!label) {
      return;
    }

    label.planeMesh.setEnabled(visible);
  }

  /**
   * Calculate label position above entity
   *
   * @param clientEntity Entity
   * @returns World position for label
   */
  private calculateLabelPosition(clientEntity: ClientEntity): Vector3 {
    // Get entity height (use model dimensions or fallback)
    const height = clientEntity.model?.dimensions?.walk?.height || 1.8;
    const labelOffset = 0.3;

    return new Vector3(
      clientEntity.currentPosition.x,
      clientEntity.currentPosition.y + height + labelOffset,
      clientEntity.currentPosition.z
    );
  }

  /**
   * Dispose all labels
   */
  dispose(): void {
    for (const label of this.entityLabels.values()) {
      label.planeMesh.dispose();
      label.guiTexture.dispose();
    }
    this.entityLabels.clear();
    logger.debug('EntityLabelRenderer disposed');
  }
}
