import Image from "next/image";
import { conceptImages } from "./landing-content";

export function ConceptPhoto({ image = "exterior", className = "", priority = false, sizes = "(max-width: 767px) 100vw, 50vw" }: {
  image?: keyof typeof conceptImages; className?: string; priority?: boolean; sizes?: string;
}) {
  const photo = conceptImages[image];
  return <figure className={`concept-photo ${className}`}>
    <Image src={photo.src} alt={photo.alt} fill priority={priority} fetchPriority={priority ? "high" : undefined} sizes={sizes} className="object-cover" />
    <figcaption className="concept-caption">Imagen conceptual · ilustrativa</figcaption>
  </figure>;
}
