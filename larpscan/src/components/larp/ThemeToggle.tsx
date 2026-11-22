import { Moon, Sun } from "lucide-react";
import { useEffect, useState } from "react";

export function ThemeToggle() {
  const [dark, setDark] = useState(false);
  useEffect(() => { setDark(document.documentElement.classList.contains("dark")); }, []);
  const toggle = () => {
    const next = !dark;
    setDark(next);
    document.documentElement.classList.toggle("dark", next);
    localStorage.setItem("larp-theme", next ? "dark" : "light");
  };
  return (
    <button onClick={toggle} aria-label="toggle dark mode"
      className="grid h-10 w-10 place-items-center rounded-full border border-border bg-background/80 text-foreground backdrop-blur transition-transform duration-300 hover:scale-105 active:scale-95">
      {dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
    </button>
  );
}
