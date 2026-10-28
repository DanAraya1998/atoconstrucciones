import type { PublicSite } from "./public-data";

export const lorem = {
  heading: "Lorem ipsum dolor sit amet.",
  introduction: "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.",
  about: "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore.",
  paragraph: "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aenean commodo ligula eget dolor. Aenean massa, cum sociis natoque penatibus.",
  services: ["Lorem ipsum dolor", "Consectetur adipiscing", "Aenean commodo"],
};

export const conceptImages = {
  exterior: { src: "/images/ato-concept-exterior.webp", alt: "Visualización conceptual de una vivienda tropical; imagen ilustrativa, no una obra de ATO." },
  interior: { src: "/images/ato-concept-interior.webp", alt: "Visualización conceptual de un interior con escalera y patio; imagen ilustrativa, no una obra de ATO." },
  detail: { src: "/images/ato-concept-detail.webp", alt: "Visualización conceptual de detalles en piedra, madera y concreto; imagen ilustrativa, no una obra de ATO." },
};

export function landingContent(site: PublicSite | null) {
  return site?.approved
    ? { sample: false, heading: site.heading, introduction: site.introduction, about: site.about, services: site.services }
    : { sample: true, heading: lorem.heading, introduction: lorem.introduction, about: lorem.about, services: lorem.services };
}
