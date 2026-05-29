import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;

public class MeuAgente extends Agente {

    Color color;

    double vel = 100;
    double ang = 0;

    double angVolante = 0;
    int volanteinfvalue = 0;
    int volanteRotationSpeed = 120;

    public int fase = 0;

    double oldx = 0;
    double oldy = 0;
    int timeria = 0;

    boolean start = false;

    ArrayList<Rectangle> listaDeObstaculos = null;

    Polygon poly = new Polygon();
    Polygon poly2 = new Polygon();

    static final double TARGET_X = 400;
    static final double TARGET_Y = 15;

    public MeuAgente(int x, int y, Color color, ArrayList<Rectangle> listaDeObstaculos) {
        X = x;
        Y = y;
        this.color = color;
        this.listaDeObstaculos = listaDeObstaculos;

        poly.addPoint(-20, -10);
        poly.addPoint(25, -10);
        poly.addPoint(25, 10);
        poly.addPoint(-20, 10);
    }

    @Override
    public void SimulaSe(int DiffTime) {
        timeria += DiffTime;

        oldx = X;
        oldy = Y;
        double oldang = ang;

        angVolante += volanteinfvalue * volanteRotationSpeed * DiffTime / 1000.0;
        if (angVolante > 90) angVolante = 90;
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
            double x = poly2.xpoints[i];
            double y = poly2.ypoints[i];
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
        if (v > 0) volanteinfvalue = 1;
        else if (v < 0) volanteinfvalue = -1;
        else volanteinfvalue = 0;
    }

    public void acelera(int v) {
        if (v > 0) {
            if (Y < 60) vel = 10;
            else if (Y < 150) vel = 30;
            else vel = 100;
        } else if (v < 0) {
            vel = -50;
        } else {
            vel = 0;
        }
    }

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

    public void calculaIA(int DiffTime) {
        if (!start) return;

        double dx = TARGET_X - X;
        double dy = Y - TARGET_Y;

        // POSIÇÃO X -> ÂNGULO ALVO
        double muMuitoEsq = rampUp(dx, 60, 120);
        double muEsq = tri(dx, 0, 60, 120);
        double muCentro = tri(dx, -60, 0, 60);
        double muDir = tri(dx, -120, -60, 0);
        double muMuitoDir = rampDown(dx, -120, -60);

        double angMuitoEsq = -0.1;
        double angEsq = -Math.PI / 4.0;
        double angCentro = -Math.PI / 2.0;
        double angDir = -3.0 * Math.PI / 4.0;
        double angMuitoDir = -Math.PI + 0.1;

        double angAlvo = muMuitoEsq * angMuitoEsq +
                muEsq * angEsq +
                muCentro * angCentro +
                muDir * angDir +
                muMuitoDir * angMuitoDir;

        //MURO
        double muPerigoMuro = rampDown(Y, 40, 150);
        double muDesalinhadoX = rampUp(Math.abs(dx), 15, 60);

        double fatorFuga = muPerigoMuro * muDesalinhadoX;
        double angFuga = (dx > 0) ? -0.05 : (-Math.PI + 0.05);

        angAlvo = (fatorFuga * angFuga) + ((1.0 - fatorFuga) * angAlvo);

        // ERRO DE ÂNGULO
        double erroAng = angAlvo - ang;
        while (erroAng > Math.PI) erroAng -= 2 * Math.PI;
        while (erroAng < -Math.PI) erroAng += 2 * Math.PI;

        double muAngMuitoEsq = rampDown(erroAng, -0.6, -0.2);
        double muAngEsq = tri(erroAng, -0.6, -0.2, 0.0);
        double muAngFrente = tri(erroAng, -0.2, 0.0, 0.2);
        double muAngDir = tri(erroAng, 0.0, 0.2, 0.6);
        double muAngMuitoDir = rampUp(erroAng, 0.2, 0.6);

        double volanteDesejado = muAngMuitoEsq * (-90) +
                muAngEsq * (-45) +
                muAngFrente * (0) +
                muAngDir * (45) +
                muAngMuitoDir * (90);

        // VELOCIDADE
        double muMuitoLonge = rampUp(dy, 90, 150);
        double muLonge = tri(dy, 40, 90, 150);
        double muPerto = tri(dy, 10, 40, 90);
        double muChegou = rampDown(dy, 10, 40);

        double velDesejada = muMuitoLonge * 100 +
                muLonge * 60 +
                muPerto * 25 +
                muChegou * 0;

        double muCurvaFechada = rampUp(Math.abs(erroAng), 0.2, 0.8);
        vel = velDesejada * (1.0 - (muCurvaFechada * 0.75));

        volanteDesejado = volanteDesejado * (1.0 - muChegou);

        if (angVolante < volanteDesejado - 5) rodaVolante(1);
        else if (angVolante > volanteDesejado + 5) rodaVolante(-1);
        else rodaVolante(0);


        double muEstacionadoPerfeitamente = muChegou * muCentro;

        if (muEstacionadoPerfeitamente > 0.80) {
            vel = 0;
            rodaVolante(0);
            start = false;
            fase = 3;
        } else {
            fase = 2;
        }
    }
}