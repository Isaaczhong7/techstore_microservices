import type { Category } from "../types";

export const PAGE_SIZE = 20;

export const categories: Category[] = [
  "LAPTOP",
  "DESKTOP",
  "SMARTPHONE",
  "TABLET",
  "MONITOR",
  "KEYBOARD",
  "MOUSE",
  "HEADPHONES",
  "SPEAKER",
  "CAMERA",
  "SMARTWATCH",
  "STORAGE",
  "MEMORY",
  "PROCESSOR",
  "GRAPHICS_CARD",
  "MOTHERBOARD",
  "POWER_SUPPLY",
  "NETWORKING",
  "ACCESSORY",
  "OTHER"
];

export const currency = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD"
});
