import type { Metadata } from "next";
import { InvitationAcceptance } from "@/features/accounts/invitations";
export const metadata:Metadata={title:"Aceptar invitación | ATO Construcciones",robots:{index:false,follow:false}};
export default function InvitationPage(){return <section className="panel mx-auto max-w-xl space-y-5"><h1 className="text-3xl font-semibold">Aceptar invitación</h1><InvitationAcceptance/></section>;}
