package dev.cuboidplots.core;

/** Grants are independent. OTHER_BLOCK never implies CONTAINER, DOOR or SWITCH. */
public enum Action {
  BREAK,
  PLACE,
  CONTAINER,
  DOOR,
  SWITCH,
  OTHER_BLOCK,
  ITEM_ON_BLOCK,
  ITEM_IN_AIR,
  ENTITY_INTERACT,
  ENTITY_DAMAGE
}
