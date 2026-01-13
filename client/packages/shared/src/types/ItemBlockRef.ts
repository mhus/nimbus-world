import type {Vector3} from "./Vector3";

export interface ItemBlockRef {

    /**
     * Unique item identifier
     * Generated on server, used for tracking and updates
     */
    id: string;

    /**
     * Position in world coordinates
     * Direct position, not wrapped in a Block
     */
    position: Vector3;

    /**
     * Texture path for the item (e.g., 'items/sword.png')
     * This is the only required field.
     */
    texture: string;

    /**
     * X-axis scaling (width)
     * Default: 0.5 (half block width)
     */
    scaleX?: number;

    /**
     * Y-axis scaling (height multiplier)
     * Default: 0.5 (half block height)
     * Final height = (texture height / texture width) * scaleY
     */
    scaleY?: number;

    /**
     * Pivot offset [x, y, z]
     * Shifts the item's center point relative to block position
     * Default: [0, 0, 0]
     *
     * Example: [0, -0.2, 0] lowers the item by 0.2 units
     */
    offset?: [number, number, number];

    /**
     * Optional display name for the item
     */
    name?: string;

    /**
     * If the item type is generic (like 'potion' or 'arrow'), this specifies the quantity.
     * For unique items (like 'sword' or 'wand'), this is not possible.
     */
    amount?: number; //javaType: int

}
