"use client";
import type { Actor } from "@/lib/api/transport";
import { useData } from "@/features/projects/shared";
import { DepositsPanel } from "@/features/deposits/deposits-panel";
import { ExpensesPanel } from "@/features/expenses/expenses-panel";
import { PaymentsPanel } from "@/features/payments/payments-panel";
import { FiscalPanel } from "@/features/fiscal/fiscal-panel";
import { Metrics,type Summary,type ClientSummary } from "./shared";
export function ProjectFinance({user,projectId,operations}:{user:Actor;projectId:string;operations:boolean}) {
  const client=useData<ClientSummary>(user,`/api/v1/projects/${projectId}/client-summary`);
  const financial=useData<Summary>(operations?user:undefined,`/api/v1/projects/${projectId}/financial-summary`);
  const c=client.data,s=financial.data;
  if(client.isPending)return <p role="status">Cargando cobros…</p>;
  if(!c)return null;
  return <section aria-label="Operación financiera" className="space-y-6"><div className="panel space-y-4"><h2 className="text-2xl font-semibold">Cobros y operación financiera</h2><p className="text-sm text-slate-600">CRC · montos brutos · corte {new Intl.DateTimeFormat("es-CR",{timeZone:"America/Costa_Rica",dateStyle:"medium",timeStyle:"short"}).format(new Date(c.asOf))}</p><Metrics items={[{label:"Contrato vigente",value:c.currentContract},{label:"Cobros aplicados",value:c.appliedCollections},{label:"Pendiente de cobro",value:c.collectionDue},{label:"Crédito del cliente",value:c.customerCredit},{label:"Depósitos por confirmar",value:c.reportedDeposits},{label:"Cobros sin aplicar",value:c.unappliedCollections}]}/>{!c.contractFormalized&&<p>El contrato está pendiente de formalización.</p>}{s&&<><h3 className="pt-4 text-xl font-semibold">Costos y caja de esta obra</h3><Metrics items={[{label:"Costo validado",value:s.validatedCosts},{label:"Pagos netos de caja",value:s.cashPayments},{label:"Deuda validada pendiente",value:s.validatedDebtPending},{label:"Saldo a favor con proveedores",value:s.supplierCredit},{label:"Anticipos sin aplicar",value:s.unappliedAdvances},{label:"Fondos atribuibles",value:s.availableProjectCash},{label:"Fondos tras obligaciones conocidas",value:s.freeAfterKnownObligations},{label:"Resultado contractual estimado",value:s.estimatedContractResult}]}/><p className="text-sm text-slate-600">Sin base presupuestaria. Los compromisos se incorporan en M5. El resultado contractual es una estimación operativa; los fondos no representan el saldo completo de una cuenta bancaria.</p></>}</div>
    <DepositsPanel user={user} projectId={projectId} operations={operations}/>
    {operations&&<><ExpensesPanel user={user} projectId={projectId}/><PaymentsPanel user={user} projectId={projectId}/><FiscalPanel user={user} projectId={projectId}/></>}
  </section>;
}
