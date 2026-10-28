import type { components } from "@/lib/api/schema";

export type SupplierCategory = components["schemas"]["SupplierCategory"];
export const supplierCategories: Record<SupplierCategory, string> = {
  MASTER_BUILDER: "Maestro de obras",
  ELECTRICIAN: "Electricista",
  PLUMBER: "Plomero",
  GLAZIER: "Vidriero",
  MACHINERY: "Maquinaria",
  FINISHES: "Acabados",
  HARDWARE_STORE: "Ferretería",
  OTHER: "Otros",
};
