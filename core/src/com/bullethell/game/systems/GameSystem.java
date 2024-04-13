package com.bullethell.game.systems;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.bullethell.game.BulletHellGame;
import com.bullethell.game.Patterns.Factory.*;
import com.bullethell.game.controllers.MovementController;
import com.bullethell.game.controllers.PlayerController;
import com.bullethell.game.entities.Bullet;
import com.bullethell.game.entities.Enemy;
import com.bullethell.game.entities.Player;
import com.bullethell.game.settings.Settings;
import com.bullethell.game.systems.Enemies.EnemyManager;
import com.bullethell.game.utils.JsonUtil;
import com.bullethell.game.utils.Renderer;
import com.bullethell.game.screens.*;
import com.bullethell.game.utils.ScrollableBackground;
import jdk.tools.jmod.Main;

import java.util.*;

public class GameSystem {
    private Settings settings;

    private final AssetHandler assetHandler = new AssetHandler();

    private ScrollableBackground background;
    private Texture player_lives;
    private int score;
    private String yourScoreName;
    BitmapFont yourBitmapFontName;

    private Player player;

    private PlayerController playerController;

    private List<Bullet> playerBullets;

    private List<Bullet> enemyBullets = new ArrayList<>();

    private Map<String, ArrayList<Enemy>> enemyList;

    private MovementController mc;

    private float timeInSeconds = 0f;
    private EntityFactory playerFactory;
    private Renderer renderer;
    boolean isCollided = false;
    int time = 3, frames = 60, counter=0;
    private int level = 0;
    private final BulletHellGame game;

    private EnemyManager enemyManager;

    private SpriteBatch spriteBatch;

    public GameSystem(BulletHellGame game, SpriteBatch spriteBatch) {
        this.game = game;
        playerFactory = new PlayerFactory();
        this.spriteBatch = spriteBatch;
    }

    public void init() {
        loadSettings();
        assetHandler.load(settings.getAssets());

        enemyList = new HashMap<>();
        // Create player using factory
        player = (Player) playerFactory.createEntity((float) Gdx.graphics.getWidth() / 2 - 66, 0, assetHandler,"Player", new Vector2(), 25, 5);

        playerController = new PlayerController(player, settings.getPlayerSettings());
        playerBullets = new ArrayList<>();

        background = new ScrollableBackground(assetHandler, 100);
        player_lives = assetHandler.getAssetTexture("lives");

        mc = new MovementController();

        renderer = new Renderer();

        enemyManager = new EnemyManager(settings.getLevelInterpreter(), renderer);

        //Initialize score
        score = 0;
        yourScoreName = "score: 0";
        yourBitmapFontName = new BitmapFont();
    }

    public void update(float time) {
        timeInSeconds += time;

        enemyManager.update(timeInSeconds, time, assetHandler, spriteBatch);

        enemyList = enemyManager.getEnemyList();

        playerController.listen(playerBullets, assetHandler, time);
        updatePlayerBullets();
        checkPlayerCollision();
        checkBulletCollision();
        checkEnemyBulletPlayerCollision();
        updateEnemyBullets();
        enemyShoot(time);
        background.update(time);
        //checkHighScore();
    }

    //TODO: move this to separate collision detection class (deliverable 3)
    private void checkPlayerCollision()  { // Player collision with Enemy
        List<Enemy> allEnemies = new ArrayList<>();
        enemyList.values().forEach(allEnemies::addAll);
        Iterator<Enemy> enemyIterator = allEnemies.iterator();
        while(enemyIterator.hasNext()){
            Enemy enemy = enemyIterator.next();
            if(player.getHitbox().overlaps(enemy.getHitbox())){

                player.lostLive(); //Decrement live for player

                System.out.println("player and enemy have collision! Remaining Lives = " + player.getLives());
                enemyIterator.remove();

                if ( !player.isGameOver()) {
                    player.setPosition(new Vector2(Gdx.graphics.getWidth() / 2f - 66, 0) ); //Spawn player in new position
                    enemyBullets = new ArrayList<>();
                }
                else if(player.isGameOver() )
                {
                    System.out.println("Player LOST - Game over");
                    //re-write a reset method later
                    //level=0;
                    timeInSeconds = 0f;
                    playerBullets.clear();
                    enemyBullets.clear();
                    enemyManager.getEnemyList().clear();
                    player.setLives(5);
                    player.setPosition(new Vector2(Gdx.graphics.getWidth() / 2f - 66, 0));  //need to replace it to diff. func.
                    game.setScreen(new GameOverScreen(game));
                }

            }
        }
    }

    //TODO: move this to separate collision detection class (deliverable 3)
    private void checkBulletCollision(){ // Player bullet to enemy collision
        for(Iterator<Bullet> bulletIterator = playerBullets.iterator(); bulletIterator.hasNext();){
            Bullet bullet = bulletIterator.next();
            boolean bulletRemoved = false;
            for(List<Enemy> enemies : enemyList.values()){
                for(Iterator<Enemy> enemyIterator = enemies.iterator(); enemyIterator.hasNext();){
                    Enemy enemy = enemyIterator.next();
                    if(bullet.getHitbox().overlaps(enemy.getHitbox())){

                        //updating score
                        score += enemy.getScore();

                        bulletIterator.remove();
                        enemy.enemyHit(player.damage); //reducing  enemy health
                        System.out.println("Bullet hit detected! - " + enemy.getHealth());
                        if(enemy.getHealth() <= 0) {
                            enemyIterator.remove();
                            score += enemy.getKillBonusScore(); // update kill score
                            checkPlayerWon(false);
                        }
                        yourScoreName="Score: "+ score;

                        bulletRemoved = true;
                        break;
                    }
                }
                if(bulletRemoved){
                    break;
                }
            }
        }
    }

    //TODO: move this to separate collision detection class (deliverable 3)
    private void checkEnemyBulletPlayerCollision() { // Enemy Bullet to player collision
        Iterator<Bullet> bulletIterator = enemyBullets.iterator();

            while (bulletIterator.hasNext()) {
                Bullet bullet = bulletIterator.next();
                if (bullet.getHitbox().overlaps(player.getHitbox()) && !isCollided) {

                    //System.out.println("Player got hit by enemy Bullet!");
                    isCollided = true;
                    player.lostLive(); //Decrement live for player
                    bulletIterator.remove();
                    counter = 0;

                    System.out.println("player and enemy bullet have collision! Remaining Lives = " + player.getLives());
                    if (!player.isGameOver()) {
                        player.setPosition(new Vector2(Gdx.graphics.getWidth() / 2f - 66, 0) ); //Spawn player in new position
                        enemyBullets = new ArrayList<>();
                    } else if (player.isGameOver()) {
                        System.out.println("Player LOST - Game over");
                        //re-write a reset method later

                        timeInSeconds = 0f;
                        playerBullets.clear();
                        enemyBullets.clear();
                        enemyManager.getEnemyList().clear();
                        player.setLives(5);
                        player.setPosition(new Vector2(Gdx.graphics.getWidth() / 2f - 66, 0));  //need to replace it to diff. func.
                        game.setScreen(new GameOverScreen(game));
                    }

                }
            }

    }
    //TODO: move this to enemy management class (deliverable 3)
    private void enemyShoot(float deltaTime) {
        for (ArrayList<Enemy> enemies : enemyList.values()) {
            for (Enemy enemy : enemies) {
                boolean isEnemyOnScreen = enemy.getPosition().y + enemy.sprite.getHeight() > 0 &&
                        enemy.getPosition().y < 720 &&
                        enemy.getPosition().x + enemy.sprite.getWidth() > 0 &&
                        enemy.getPosition().x < 1280;
                if (isEnemyOnScreen && enemy.isReadyToShoot(deltaTime)) {
                    Vector2 direction = new Vector2(
                            player.getPosition().x + player.sprite.getWidth() / 2 - (enemy.getPosition().x + enemy.sprite.getWidth() / 2),
                            player.getPosition().y + player.sprite.getHeight() / 2 - (enemy.getPosition().y + enemy.sprite.getHeight() / 2)
                    ).nor();

                    float bulletSpeed = 3;
                    Vector2 velocity = direction.scl(bulletSpeed);

                    float bulletX = enemy.getPosition().x + (enemy.sprite.getWidth() / 2) - Bullet.HITBOX_WIDTH / 2;
                    float bulletY = enemy.getPosition().y - Bullet.HITBOX_HEIGHT;
                    Bullet enemyBullet = new Bullet(bulletX, bulletY, "bullet", velocity, 25, assetHandler);

                    if(!isCollided) {
                        this.enemyBullets.add(enemyBullet);
                        //System.out.println(counter);
                        enemy.resetShootTimer();
                    }

                }
            }
            counter++;
        }
        if (counter == time * frames) {isCollided = false;}

    }

    //TODO: move this to enemy management class (deliverable 3)
    private void updateEnemyBullets() {
        Iterator<Bullet> iterator = enemyBullets.iterator();
        while(iterator.hasNext()) {
            Bullet bullet = iterator.next();
            bullet.update();
            if (bullet.getPosition().y < 0 || bullet.getPosition().y > Gdx.graphics.getHeight()) {
                iterator.remove();
            }
        }
    }
    private void renderEnemyBullets() {
        for (Bullet bullet : enemyBullets) {
            spriteBatch.draw(bullet.sprite, bullet.getPosition().x, bullet.getPosition().y);
        }
    }
    public void render(float time) {
        // combined renders
        update(time);
        background.render(spriteBatch);
//        renderer.renderBackground(spriteBatch, background);
        renderer.renderEntity(spriteBatch, player, true); // render player
        enemyManager.renderEnemies(time, spriteBatch);
        renderPlayerBullets();
        renderEnemyBullets();
        renderLives(spriteBatch, player.getLives());
        renderScore(spriteBatch);
    }
    private void renderScore(SpriteBatch spriteBatch) {
        yourBitmapFontName.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        yourBitmapFontName.draw(spriteBatch, yourScoreName,10, Gdx.graphics.getHeight()-20);

    }
    private void renderLives(SpriteBatch spriteBatch, int lives) {
        for(int i=0;i<lives; i++) {
            spriteBatch.draw(player_lives,Gdx.graphics.getWidth() - 50f / 2f * (i + 1) - 30,Gdx.graphics.getHeight() - 30, 30, 30);
        }
    }
    private void loadSettings() {
        JsonUtil jsonUtil = new JsonUtil();
        settings = jsonUtil.deserializeJson("settings/settings.json", Settings.class);
    }
    private void renderPlayerBullets() {
        for (Bullet bullet : playerBullets) {
            renderer.renderEntity(spriteBatch, bullet, true);
        }
    }
    private void updatePlayerBullets() {
        List<Bullet> removeList = new ArrayList<>();
        for (Bullet bullet : playerBullets) {
            if (bullet.getPosition().y > Gdx.graphics.getHeight()) {
                removeList.add(bullet);
            }
            bullet.update();
        }
        if (!removeList.isEmpty()) {
            playerBullets.removeAll(removeList);
        }
    }
    public void toMainMenu(){
        game.setScreen(new GameOverScreen(game));
    }

    //winning condition, need winning screen changes
    private void checkPlayerWon(boolean nextEnemy) {
        //Add winning condition
        if(enemyManager.currentWave == 3 && !nextEnemy) {
            System.out.println("Player won - Game over");
            game.setScreen(new GameWinScreen(game));
        }
    }
}
