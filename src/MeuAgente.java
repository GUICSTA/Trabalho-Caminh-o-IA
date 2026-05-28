import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;

public class MeuAgente extends Agente {

    Color color;

    double vel = 100;
    double ang  = 0;

    double angVolante = 0;
    int volanteinfvalue = 0;
    int volanteRotationSpeed = 120;

    int estado = 0;

    // ── MÁQUINA DE ESTADOS ──
    // 0 = mover diretamente para (TARGET_X, Y_APPROACH) ignorando ang
    // 1 = parar e forçar ang = -PI/2
    // 2 = subir reto até a vaga
    // 3 = parado na vaga
    int fase = 0;
    int ticksParado = 0;

    double oldx = 0;
    double oldy = 0;

    int timeria = 0;

    boolean colidiu = false;
    boolean start = false;

    ArrayList<Rectangle> listaDeObstaculos = null;

    Polygon poly  = new Polygon();
    Polygon poly2 = new Polygon();

    // Ponto de entrada da vaga (centro da abertura 385-415)
    static final double TARGET_X  = 400;

    // CORREÇÃO: Alvo aprofundado na vaga (era 25)
    static final double TARGET_Y  = 15;

    // Ponto de aproximação: alinha X nesta altura antes de subir
    static final double APPROACH_Y = 180;

    public MeuAgente(int x, int y, Color color, ArrayList<Rectangle> listaDeObstaculos) {
        X = x;
        Y = y;
        this.color = color;
        this.listaDeObstaculos = listaDeObstaculos;

        poly.addPoint(-20, -10);
        poly.addPoint(25,  -10);
        poly.addPoint(25,   10);
        poly.addPoint(-20,  10);
    }

    @Override
    public void SimulaSe(int DiffTime) {
        timeria += DiffTime;

        oldx = X;
        oldy = Y;
        double oldang = ang;

        angVolante += volanteinfvalue * volanteRotationSpeed * DiffTime / 1000.0;
        if (angVolante >  90) angVolante =  90;
        if (angVolante < -90) angVolante = -90;

        if (timeria > 100) {
            calculaIA(DiffTime);
            timeria = 0;
        }

        if (start) {
            ang += vel / 100.0 * ((angVolante * Math.PI / 2) / 90.0f) * DiffTime / 1000.0;
            X += Math.cos(ang) * vel * DiffTime / 1000.0;
            Y += Math.sin(ang) * vel * DiffTime / 1000.0;
        }

        poly2 = new Polygon(poly.xpoints, poly.ypoints, poly.npoints);
        for (int i = 0; i < poly2.npoints; i++) {
            double x  = poly2.xpoints[i];
            double y  = poly2.ypoints[i];
            double x2 = x * Math.cos(ang) - y * Math.sin(ang);
            double y2 = y * Math.cos(ang) + x * Math.sin(ang);
            poly2.xpoints[i] = (int) x2;
            poly2.ypoints[i] = (int) y2;
        }
        poly2.translate((int) X, (int) Y);

        for (int i = 0; i < listaDeObstaculos.size(); i++) {
            if (poly2.intersects(listaDeObstaculos.get(i))) {
                X = oldx;
                Y = oldy;
                ang = oldang;
                break;
            }
        }
    }

    @Override
    public void DesenhaSe(Graphics2D dbg, int XMundo, int YMundo) {
        dbg.setColor(Color.red);
        dbg.draw(poly2);

        dbg.setColor(color);
        AffineTransform trans = dbg.getTransform();
        dbg.translate(X, Y);
        dbg.rotate(ang);
        dbg.drawRect(-20, -10, 40, 20);
        dbg.drawRect(20, -5, 5, 10);
        dbg.setTransform(trans);
    }

    public void rodaVolante(int v) {
        if (v > 0)      volanteinfvalue =  1;
        else if (v < 0) volanteinfvalue = -1;
        else            volanteinfvalue =  0;
    }

    public void acelera(int v) {
        if (v > 0) {
            if (Y < 60)       vel = 10;   // rasteja na entrada da vaga
            else if (Y < 150) vel = 30;
            else              vel = 100;
        } else if (v < 0) {
            vel = -50;
        } else {
            vel = 0;
        }
    }

    // =====================================================================
    //  FUNÇÕES DE PERTINÊNCIA FUZZY
    // =====================================================================

    private double rampUp(double x, double a, double b) {
        if (x <= a) return 0.0;
        if (x >= b) return 1.0;
        return (x - a) / (b - a);
    }

    private double rampDown(double x, double a, double b) {
        return 1.0 - rampUp(x, a, b);
    }

    private double tri(double x, double a, double m, double b) {
        if (x <= a || x >= b) return 0.0;
        if (x <= m) return (x - a) / (m - a);
        return (b - x) / (b - m);
    }

    private double trap(double x, double a, double b, double c, double d) {
        if (x <= a || x >= d) return 0.0;
        if (x >= b && x <= c) return 1.0;
        if (x < b) return (x - a) / (b - a);
        return (d - x) / (d - c);
    }

    // =====================================================================
    //  DEFUZZIFICAÇÃO
    // =====================================================================

    private int defuzzVolante(double muEsq, double muReto, double muDir) {
        double num = (-1.0) * muEsq + 0.0 * muReto + 1.0 * muDir;
        double den = muEsq + muReto + muDir;
        if (den == 0) return 0;
        double out = num / den;
        if (out < -0.3) return -1;
        if (out >  0.3) return  1;
        return 0;
    }

    private int defuzzAcel(double muRe, double muPara, double muFrente) {
        double num = (-1.0) * muRe + 0.0 * muPara + 1.0 * muFrente;
        double den = muRe + muPara + muFrente;
        if (den == 0) return 0;
        double out = num / den;
        if (out < -0.3) return -1;
        if (out >  0.3) return  1;
        return 0;
    }

    // =====================================================================
    //  IA PRINCIPAL
    // =====================================================================

    public void calculaIA(int DiffTime) {
        if (!start) return;

        // ── FASE 0: navegar até o ponto de aproximação ────────────────
        if (fase == 0) {
            double dx = TARGET_X - X;
            double dy = APPROACH_Y - Y;
            double dist = Math.sqrt(dx * dx + dy * dy);

            // Alvo dinâmico: enquanto longe, navega em linha reta para o ponto
            double angAlvo = Math.atan2(dy, dx);

            double erroAng = angAlvo - ang;
            while (erroAng >  Math.PI) erroAng -= 2 * Math.PI;
            while (erroAng < -Math.PI) erroAng += 2 * Math.PI;

            // Funções de pertinência para erro angular
            double muAngMuitoEsq = rampDown(erroAng, -2.0, -0.6);
            double muAngEsq      = tri(erroAng, -2.0, -0.6, -0.1);
            double muAngFrente   = tri(erroAng, -0.3,  0.0,  0.3);
            double muAngDir      = tri(erroAng,  0.1,  0.6,  2.0);
            double muAngMuitoDir = rampUp(erroAng,  0.6,  2.0);

            double muVolanteEsq  = Math.max(muAngMuitoEsq, muAngEsq * 0.8);
            double muVolanteReto = muAngFrente;
            double muVolanteDir  = Math.max(muAngMuitoDir, muAngDir * 0.8);

            // Sempre anda — se o erro for grande, gira enquanto anda
            double muAcelFrente = (dist > 25) ? 1.0 : 0.5;
            double muAcelPara   = (dist <= 25) ? 1.0 : 0.0;
            double muAcelRe     = 0;

            rodaVolante(defuzzVolante(muVolanteEsq, muVolanteReto, muVolanteDir));
            acelera(defuzzAcel(muAcelRe, muAcelPara, muAcelFrente));

            // CORREÇÃO: Garante o alinhamento de X e Y antes de mudar de fase
            if (Math.abs(dx) < 5 && Math.abs(dy) < 15) {
                vel = 0;
                angVolante = 0;
                volanteinfvalue = 0;
                ticksParado = 0;
                fase = 1;
            }
            return;
        }

        // ── FASE 1: parar e forçar ang = -PI/2 ────────────────────────
        if (fase == 1) {
            vel = 0;
            angVolante = 0;
            volanteinfvalue = 0;
            ticksParado++;

            if (ticksParado >= 3) {
                ang = -Math.PI / 2;
                fase = 2;
            }
            return;
        }

        // ── FASE 2: subir reto para a vaga (fuzzy) ────────────────────
        if (fase == 2) {
            double dx   = TARGET_X - X;
            double dy   = TARGET_Y - Y;
            double dist = Math.sqrt(dx * dx + dy * dy);

            double angAlvo = -Math.PI / 2;
            double erroAng = angAlvo - ang;
            while (erroAng >  Math.PI) erroAng -= 2 * Math.PI;
            while (erroAng < -Math.PI) erroAng += 2 * Math.PI;

            double muAngMuitoEsq = rampDown(erroAng, -1.5, -0.3);
            double muAngEsq      = tri(erroAng, -1.5, -0.3, -0.05);
            double muAngFrente   = tri(erroAng, -0.15, 0.0,  0.15);
            double muAngDir      = tri(erroAng,  0.05,  0.3,  1.5);
            double muAngMuitoDir = rampUp(erroAng,  0.3,  1.5);

            double muVolanteEsq  = Math.max(muAngMuitoEsq, muAngEsq * 0.8);
            double muVolanteReto = muAngFrente;
            double muVolanteDir  = Math.max(muAngMuitoDir, muAngDir * 0.8);

            // CORREÇÃO: Proíbe correções laterais perto da parede (Y < 60)
            if (Math.abs(dx) > 5 && Y > 60) {
                double muXEsq = rampDown(dx, -30, -3); // Ativa se o caminhão está muito à direita
                double muXDir = rampUp(dx,    3,  30); // Ativa se o caminhão está muito à esquerda

                // Se está à direita, vira para a ESQUERDA
                muVolanteEsq = Math.max(muVolanteEsq, muXEsq * 1.0);
                // Se está à esquerda, vira para a DIREITA
                muVolanteDir = Math.max(muVolanteDir, muXDir * 1.0);
            }

            // CORREÇÃO: Diminui a zona de frenagem para ele entrar mais fundo
            double muAcelFrente = (dist > 5) ? 0.8 : 0.0;
            double muAcelPara   = (dist <= 5) ? 1.0 : 0.0;
            double muAcelRe     = 0;

            rodaVolante(defuzzVolante(muVolanteEsq, muVolanteReto, muVolanteDir));
            acelera(defuzzAcel(muAcelRe, muAcelPara, muAcelFrente));

            // Parada: Y próximo do topo e X dentro da abertura real (385-415)
            boolean dentroAbertura = (X > 384 && X < 416);

            // CORREÇÃO: Exige que suba mais na tela para completar o estacionamento
            boolean entrou = (Y < 20);

            if (dentroAbertura && entrou) {
                fase = 3;
            }
            return;
        }

        //parado na vaga
        if (fase == 3) {
            rodaVolante(0);
            acelera(0);
            vel = 0;
            start = false;
        }
    }
}