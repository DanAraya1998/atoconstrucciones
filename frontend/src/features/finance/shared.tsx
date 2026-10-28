"use client";
import type { components } from "@/lib/api/schema";
import { crcAmount } from "@/features/projects/shared";
import { useState } from "react";
export type Money=components["schemas"]["Money"];
export type Deposit=components["schemas"]["DepositReport"];
export type Movement=components["schemas"]["TreasuryMovement"];
export type Expense=components["schemas"]["ProjectExpense"];
export type Voucher=components["schemas"]["FiscalVoucher"];
export type Account=components["schemas"]["TreasuryAccount"];
export type Summary=components["schemas"]["ProjectFinancialSummary"];
export type ClientSummary=components["schemas"]["ClientFinancialSummary"];
export function Amount({value}:{value:Money}) {return <span className="tabular-nums">₡ {crcAmount(value.amount)}</span>;}
export function MoneyField({label,name,value="",required=true}:{label:string;name:string;value?:string;required?:boolean}) {return <label>{label}<input aria-label={label} name={name} inputMode="decimal" pattern="[0-9]+([.][0-9]{1,5})?" defaultValue={value} required={required} placeholder="0.00"/><span className="text-xs text-slate-500">CRC · utiliza punto decimal</span></label>;}
export function Choice({label,name,items,required=true,value="",onChange}:{label:string;name:string;items:{id:string;name:string}[];required?:boolean;value?:string;onChange?:(value:string)=>void}) {const [selected,setSelected]=useState(value);return <label>{label}<select aria-label={label} name={name} required={required} value={selected} onChange={event=>{setSelected(event.target.value);onChange?.(event.target.value);}}><option value="">{required?"Selecciona una opción":"Sin selección"}</option>{items.map(i=><option key={i.id} value={i.id}>{i.name}</option>)}</select></label>;}
export function today() {return new Intl.DateTimeFormat("en-CA",{timeZone:"America/Costa_Rica",year:"numeric",month:"2-digit",day:"2-digit"}).format(new Date());}
const states:Record<string,string>={REPORTED:"Reportado",IN_REVIEW:"En revisión",CONFIRMED:"Confirmado",REJECTED:"Rechazado",DUPLICATE:"Duplicado vinculado",REVERSED:"Revertido",REGISTERED:"Registrado",VALIDATED:"Validado",DRAFT:"Preparado",RECEIPT:"Cobro",PAYMENT:"Pago",ADVANCE:"Anticipo",SUPPLIER_REFUND:"Reembolso del proveedor",CUSTOMER_REFUND:"Devolución al cliente",FINANCING:"Aporte financiero",OTHER_OUT:"Otra salida",TRANSFER_IN:"Transferencia recibida",TRANSFER_OUT:"Transferencia enviada"};
export const label=(state:string)=>states[state]??state;
export function Metrics({items}:{items:{label:string;value:Money}[]}) {return <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{items.map(i=><div key={i.label} className="rounded-xl border border-border bg-white p-4"><dt className="text-sm text-slate-600">{i.label}</dt><dd className="mt-2 text-xl font-semibold"><Amount value={i.value}/></dd></div>)}</dl>;}
