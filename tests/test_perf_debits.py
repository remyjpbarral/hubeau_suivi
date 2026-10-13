"""Test rapide des débits (exécution directe : python3 tests/test_perf_debits.py)."""
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from suivi_eau.collecteurs.debits import CollecteurDebits  # noqa: E402

t0 = time.time()
m, s = CollecteurDebits().collecte(code_cours_eau="la vis",
                                    date_debut="2024-01-01")
print("DEBITS la vis:", len(m), "mesures,", len(s), "stations en",
      round(time.time() - t0, 1), "s")
