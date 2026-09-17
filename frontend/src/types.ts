import type React from "react";

export type Page = "customer" | "checkout" | "seller";
export type CustomerType = "GUEST" | "MEMBER";

export type Category =
  | "LAPTOP"
  | "DESKTOP"
  | "SMARTPHONE"
  | "TABLET"
  | "MONITOR"
  | "KEYBOARD"
  | "MOUSE"
  | "HEADPHONES"
  | "SPEAKER"
  | "CAMERA"
  | "SMARTWATCH"
  | "STORAGE"
  | "MEMORY"
  | "PROCESSOR"
  | "GRAPHICS_CARD"
  | "MOTHERBOARD"
  | "POWER_SUPPLY"
  | "NETWORKING"
  | "ACCESSORY"
  | "OTHER";

export type Product = {
  productId: string;
  productName: string;
  description: string;
  category: Category;
  price: number;
  active: boolean;
  quantity?: number;
  itemSold?: number;
};

export type InventoryItem = {
  productId: string;
  quantity: number;
  itemSold: number;
  version: number;
};

export type OrderStatus = "PENDING" | "PARTIAL" | "CONFIRMED" | "CANCELLED" | "EXPIRED" | "CONFIRMING" | "CANCELLING";
export type PaymentStatus = "PENDING" | "CONFIRMING" | "SUCCEEDED" | "FAILED" | "CANCELLING" | "CANCELLED";

export type OrderResult = {
  orderId: string;
  paymentId?: string;
  reservationId?: string;
  status: OrderStatus;
  expiresAt?: string;
};

export type OrderItemEntry = {
  productId: string;
  quantity: number;
  unitPrice?: number;
  status: string;
};

export type OrderEntry = {
  id: string;
  reservationId?: string;
  paymentId?: string;
  customerId?: string;
  email: string;
  customerType: CustomerType;
  status: OrderStatus;
  paymentStatus?: PaymentStatus;
  totalAmount?: number;
  createdAt?: string;
  expiresAt?: string;
  items?: OrderItemEntry[];
};

export type CartLine = {
  product: Product;
  quantity: number;
};

export type Toast = {
  tone: "success" | "error" | "info";
  text: string;
};

export type OrderForm = {
  email: string;
  customerType: CustomerType;
  customerId: string;
};

export type PaymentForm = {
  orderId: string;
  paymentId: string;
  paymentMethodId: string;
  currencyType: string;
};

export type ProductForm = {
  productName: string;
  description: string;
  category: Category;
  price: string;
  active: boolean;
  initialStock: string;
};

export type StateSetter<T> = React.Dispatch<React.SetStateAction<T>>;
