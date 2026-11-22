import { AnimatePresence, motion } from "framer-motion";
import { useEffect, useMemo, useState } from "react";
import { FIGLETS } from "./figlets";

const MESSAGES = [
  "on your larps, get set...", "larpity larp larp...", "larping up a storm...", "initiating L.A.R.P. protocol...",
  "mastering the art of the larp...", "larp me up, Scotty...", "who you gon' call...", "larping into hyperspace...",
  "larping like nobody's watching...",
];
const EMOJIS = ["📖", "🐦", "🍃", "🧌", "🎭", "😢", "🥀", "✌️", "😱", "😎", "😏", "🤥", "😴", "🥱", "🤪"];
const SHADES = [
  "bg-splash-coral", "bg-splash-gold", "bg-splash-mint", "bg-splash-sky", "bg-splash-lilac",
] as const;

export function Splash({ onDone }: { onDone: () => void }) {
  const [state, setState] = useState<"pending" | "show" | "burst" | "gone">("pending");
  const [msg, setMsg] = useState("");
  const [fontIndex, setFontIndex] = useState(0);
  const [shade, setShade] = useState<(typeof SHADES)[number]>("bg-splash-coral");
  const offsets = useMemo(() => [0, 1, 2, 3].map((i) => ({ y: [-6, 10, -2, 6][i] ?? 0, r: [-3, 2, -1.5, 3][i] ?? 0 })), []);
  const [particles, setParticles] = useState<{ e: string; x: number; y: number; r: number; s: number }[]>([]);
  const figlet = FIGLETS[fontIndex] ?? FIGLETS[0]!;
  const maxChars = Math.max(...figlet.letters.flatMap((letter) => letter.map((row) => row.length)));

  useEffect(() => {
    setMsg(MESSAGES[Math.floor(Math.random() * MESSAGES.length)] ?? MESSAGES[0]!);
    setFontIndex(Math.floor(Math.random() * FIGLETS.length));
    setShade(SHADES[Math.floor(Math.random() * SHADES.length)] ?? SHADES[0]);
    setState("show");
    const t1 = setTimeout(() => {
      setParticles(Array.from({ length: 64 }, () => {
        const a = Math.random() * Math.PI * 2, d = 220 + Math.random() * 600;
        return { e: EMOJIS[Math.floor(Math.random() * EMOJIS.length)]!, x: Math.cos(a) * d, y: Math.sin(a) * d, r: (Math.random() - 0.5) * 720, s: 0.8 + Math.random() * 1.7 };
      }));
      setState("burst");
      onDone();
    }, 1800);
    const t2 = setTimeout(() => setState("gone"), 3200);
    return () => { clearTimeout(t1); clearTimeout(t2); };
  }, [onDone]);

  if (state === "gone") return null;
  return (
    <div className="pointer-events-none fixed inset-0 z-50 overflow-hidden">
      <AnimatePresence>
        {state !== "burst" && (
          <motion.div key="bg" className={`absolute inset-0 ${shade}`}
            exit={{ opacity: 0, transition: { duration: 0.9, ease: [0.22, 1, 0.36, 1] } }} />
        )}
      </AnimatePresence>
      <AnimatePresence>
        {state === "show" && (
          <motion.div key="logo" className="absolute inset-0 flex flex-col items-center justify-center overflow-hidden text-splash-foreground"
            exit={{ scale: 1.6, opacity: 0, filter: "blur(12px)", transition: { duration: 0.6, ease: [0.22, 1, 0.36, 1] } }}>
            <div className="flex max-w-full items-center justify-center gap-0 leading-[1.05]"
              style={{ fontSize: `min(16px, ${140 / maxChars}vw)` }}>
              {figlet.letters.map((rows, i) => (
                <motion.pre key={i} className="m-0 font-mono"
                  initial={{ y: "-110vh", rotate: 0 }}
                  animate={{ y: offsets[i]!.y, rotate: offsets[i]!.r }}
                  transition={{ type: "spring", stiffness: 260, damping: 13, mass: 1.1, delay: 0.08 * i }}>
                  {rows.join("\n")}
                </motion.pre>
              ))}
            </div>
            <motion.p className="mt-8 px-6 text-center text-sm italic opacity-80" initial={{ opacity: 0 }} animate={{ opacity: 0.8 }} transition={{ delay: 0.6 }}>
              {msg}
            </motion.p>
          </motion.div>
        )}
      </AnimatePresence>
      {state === "burst" && particles.map((p, i) => (
        <motion.span key={i} className="absolute left-1/2 top-1/2 text-3xl"
          initial={{ x: 0, y: 0, opacity: 1, scale: 0.2, rotate: 0 }}
          animate={{ x: p.x, y: p.y + 120, opacity: 0, scale: p.s, rotate: p.r }}
          transition={{ duration: 1.2, ease: [0.16, 1, 0.3, 1] }}>
          {p.e}
        </motion.span>
      ))}
    </div>
  );
}
