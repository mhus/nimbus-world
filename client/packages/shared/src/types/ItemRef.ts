export interface ItemRef {

    /**
     * Unique item identifier
     */
    itemId: string;

    /**
     * Texture path for the item (e.g., 'items/sword.png')
     * This is the only required field.
     */
    texture: string;

    /**
     * Optional display name for the item
     */
    name: string;

    /**
     * If the item type is generic (like 'potion' or 'arrow'), this specifies the quantity.
     * For unique items (like 'sword' or 'wand'), this is not possible.
     */
    amount: number; //javaType: int
}
