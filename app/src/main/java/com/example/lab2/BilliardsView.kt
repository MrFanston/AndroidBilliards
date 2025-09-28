package com.example.billiard // Пакет, в котором находится класс

import android.content.Context // Импорт для доступа к ресурсам и информации о приложении
import android.graphics.Color // Импорт для работы с цветами
import android.graphics.Paint // Импорт для настроек отрисовки (кисти, цвета, стили)
import android.view.MotionEvent // Импорт для обработки событий касания экрана
import android.view.SurfaceHolder // Импорт для управления поверхностью отрисовки
import android.view.SurfaceView // Импорт базового класса для создания кастомной поверхности отрисовки
import com.example.lab2.Ball // Импорт класса Ball
import kotlin.math.* // Импорт математических функций (sqrt, cos, sin, atan2, min)

/**
 * BilliardsView - это кастомный View для отображения и управления игрой в бильярд.
 * Он использует SurfaceView для более эффективной отрисовки графики в отдельном потоке.
 */
class BilliardsView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    // --- Поля класса ---

    private val balls = mutableListOf<Ball>() // Список всех шаров на столе
    private lateinit var cueBall: Ball // Белый шар, инициализируется позже
    private val paint = Paint() // Основной объект Paint для различных нужд отрисовки
    private var running = false // Флаг, указывающий, запущен ли игровой цикл
    private lateinit var gameThread: Thread // Поток, в котором будет выполняться игровая логика и отрисовка

    // Координаты последнего касания или текущей точки прицеливания
    private var touchX = 0f
    private var touchY = 0f
    private var isAiming = false // Флаг, указывающий, находится ли игрок в режиме прицеливания

    private val pockets = mutableListOf<Pocket>() // Список луз на бильярдном столе

    /**
     * Блок инициализации класса. Выполняется при создании экземпляра BilliardsView.
     */
    init {
        holder.addCallback(this) // Регистрируем этот класс как обработчик событий жизненного цикла SurfaceHolder
    }

    /**
     * Вызывается немедленно после создания поверхности (Surface).
     * Здесь происходит начальная настройка игры: создание шаров, луз и запуск игрового потока.
     * @param holder SurfaceHolder, который содержит поверхность.
     */
    override fun surfaceCreated(holder: SurfaceHolder) {
        val radius = 40f // Радиус бильярдных шаров
        val w = width.toFloat() // Ширина View
        val h = height.toFloat() // Высота View

        // Инициализация белого шара
        cueBall = Ball(w / 2, h - 200f, radius, 0, Color.WHITE)
        balls.add(cueBall) // Добавляем в общий список шаров

        // Инициализация луз
        val pocketR = 60f // Радиус луз
        pockets.clear() // Очищаем список луз на случай пересоздания поверхности
        // Угловые лузы
        pockets.add(Pocket(0f, 0f, pocketR))
        pockets.add(Pocket(w, 0f, pocketR))
        pockets.add(Pocket(0f, h, pocketR))
        pockets.add(Pocket(w, h, pocketR))
        // Средние лузы на боковых сторонах
        pockets.add(Pocket(0f, h / 2f, pocketR))
        pockets.add(Pocket(w, h / 2f, pocketR))

        // Формирование пирамиды шаров по правилам "восьмерки"
        val startX = w / 2 // Начальная X-координата для центра пирамиды
        val startY = 300f // Начальная Y-координата для вершины пирамиды
        val rows = 5 // Количество рядов в пирамиде

        // Подготовка номеров шаров для расстановки
        val solidBalls = mutableListOf(1, 2, 3, 4, 5, 6, 7) // "Сплошные" шары
        val stripedBalls = mutableListOf(9, 10, 11, 12, 13, 14, 15) // "Полосатые" шары
        val blackBall = 8 // Черный шар

        val triangleBalls = mutableListOf<Int>() // Список номеров шаров в порядке их расположения в треугольнике

        // Вершина пирамиды (ряд 1) - всегда сплошной шар
        triangleBalls.add(solidBalls.random().also { solidBalls.remove(it) })

        // Остальные 13 шаров перемешиваются
        val remainingBalls = (solidBalls + stripedBalls).shuffled()
        triangleBalls.addAll(remainingBalls)

        // Вставка 8-го шара
        if (triangleBalls.size > 8) { // Проверка, чтобы не выйти за пределы списка
            triangleBalls.add(8, blackBall) // Добавляем 8-ку на 9-ю позицию (индекс 8)
        } else {
            triangleBalls.add(blackBall) // Если шаров мало, просто добавляем в конец
        }

        // Размещение шаров в пирамиде на столе
        var ballIndex = 0 // Индекс для выбора номера шара из triangleBalls
        for (row in 0 until rows) { // Итерация по рядам пирамиды
            val actualBallsInRow = rows - row // Количество шаров в текущем ряду (5, 4, 3, 2, 1)
            val yPos = startY + row * (radius * 2 * 0.866f)
            val currentStartX = startX - (actualBallsInRow - 1) * radius // Начальный X для текущего ряда

            for (i in 0 until actualBallsInRow) {
                val xPos = currentStartX + i * (radius * 2)
                if (ballIndex < triangleBalls.size) {
                    val number = triangleBalls[ballIndex]
                    val color = getBallColor(number)
                    balls.add(Ball(xPos, yPos, radius, number, color))
                    ballIndex++
                } else if (ballIndex == triangleBalls.size && !triangleBalls.contains(blackBall)) {
                    // Если восьмерка не была добавлена и все остальные шары расставлены,
                    // пытаемся добавить ее на свободное место
                    val number = blackBall
                    val color = getBallColor(number)
                    balls.add(Ball(xPos, yPos, radius, number, color))
                    triangleBalls.add(blackBall) // Отмечаем, что восьмерка добавлена
                    ballIndex++
                }
            }
        }


        running = true // Устанавливаем флаг, что игровой цикл запущен
        gameThread = Thread(this) // Создаем новый поток для игры, передавая текущий объект BilliardsView (который Runnable)
        gameThread.start() // Запускаем игровой поток
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    }

    /**
     * Вызывается непосредственно перед уничтожением поверхности.
     * Здесь необходимо остановить игровой поток.
     * @param holder SurfaceHolder.
     */
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false // Устанавливаем флаг, что игровой цикл должен завершиться
        var retry = true
        while (retry) {
            try {
                gameThread.join() // Ожидаем завершения игрового потока
                retry = false
            } catch (e: InterruptedException) {
                // Поток был прерван во время ожидания, повторяем попытку
                e.printStackTrace()
            }
        }
    }

    /**
     * Основной метод игрового цикла, выполняется в отдельном потоке (gameThread).
     * Здесь происходит обновление состояния игры и отрисовка каждого кадра.
     */
    override fun run() {
        while (running) { // Пока флаг running установлен в true
            if (!holder.surface.isValid) { // Проверяем, действительна ли поверхность для рисования
                continue // Если нет, пропускаем итерацию цикла
            }
            val canvas = holder.lockCanvas() // Блокируем Canvas для рисования. Только этот поток теперь может на нем рисовать.
            try {
                synchronized(holder) { // Синхронизируемся по holder для потокобезопасного доступа
                    canvas.drawColor(Color.rgb(0, 100, 0)) // Очищаем Canvas и рисуем зеленый фон стола

                    // Отрисовка луз
                    paint.color = Color.BLACK // Устанавливаем черный цвет для луз
                    pockets.forEach { pocket -> // Для каждой лузы в списке
                        canvas.drawCircle(pocket.x, pocket.y, pocket.r, paint) // Рисуем круг
                    }

                    // Обновляем состояние всех шаров (движение, столкновения со стенами)
                    balls.forEach { it.update(width.toFloat(), height.toFloat()) }
                    handleCollisions() // Обрабатываем столкновения между шарами
                    checkPockets() // Проверяем, не попал ли какой-либо шар в лузу
                    // Отрисовываем все шары
                    balls.forEach { it.draw(canvas, paint) }

                    // Отрисовка линии прицела, если игрок целится
                    if (isAiming) {
                        paint.color = Color.WHITE // Устанавливаем белый цвет для линии
                        paint.strokeWidth = 5f // Устанавливаем толщину линии
                        canvas.drawLine(cueBall.x, cueBall.y, touchX, touchY, paint) // Рисуем линию от битка до точки касания
                    }
                }
            } finally {
                holder.unlockCanvasAndPost(canvas) // Разблокируем Canvas и отображаем нарисованное
            }
        }
    }

    /**
     * Обрабатывает события касания экрана.
     * @param event Объект MotionEvent, содержащий информацию о событии касания.
     * @return true, если событие было обработано, иначе false.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) { // Определяем тип действия касания
            MotionEvent.ACTION_DOWN -> { // Пользователь коснулся экрана
                isAiming = true // Включаем режим прицеливания
                touchX = event.x // Запоминаем X-координату касания
                touchY = event.y // Запоминаем Y-координату касания
            }
            MotionEvent.ACTION_MOVE -> { // Пользователь двигает пальцем по экрану
                if (isAiming) { // Если в режиме прицеливания
                    touchX = event.x // Обновляем X-координату для линии прицела
                    touchY = event.y // Обновляем Y-координату для линии прицела
                }
            }
            MotionEvent.ACTION_UP -> { // Пользователь отпустил палец от экрана
                if (isAiming) { // Если было прицеливание
                    val dx = cueBall.x - event.x // Разница по X между белым шаром и точкой отпускания
                    val dy = cueBall.y - event.y // Разница по Y между белым шаром и точкой отпускания
                    val dist = sqrt(dx * dx + dy * dy) // Расстояние
                    val power = min(dist / 10, 40f) // Расчет силы удара (ограничена максимальным значением 40f)
                    // Чем дальше оттянуть, тем сильнее удар
                    val angle = atan2(dy, dx) // Угол удара в радианах
                    // Устанавливаем скорость белого шара на основе силы и угла
                    cueBall.vx = cos(angle) * power
                    cueBall.vy = sin(angle) * power
                    isAiming = false // Выключаем режим прицеливания
                }
            }
        }
        return true // Возвращаем true, указывая, что событие обработано этим View
    }

    /**
     * Обрабатывает столкновения между всеми парами шаров на столе.
     */
    private fun handleCollisions() {
        for (i in 0 until balls.size) { // Итерация по всем шарам (первый шар в паре)
            for (j in i + 1 until balls.size) { // Итерация по оставшимся шарам (второй шар в паре)
                val a = balls[i] // Первый шар
                val b = balls[j] // Второй шар
                val dx = b.x - a.x // Разница по X между центрами шаров
                val dy = b.y - a.y // Разница по Y между центрами шаров
                val dist = sqrt(dx * dx + dy * dy) // Расстояние между центрами шаров

                if (dist < a.r + b.r) { // Если расстояние меньше суммы радиусов, значит шары столкнулись
                    // --- Логика обработки упругого столкновения ---
                    val nx = dx / dist // Нормаль столкновения по X (единичный вектор направления от a к b)
                    val ny = dy / dist // Нормаль столкновения по Y

                    // Сохраняем проекции скоростей на нормаль
                    val va_n = a.vx * nx + a.vy * ny
                    val vb_n = b.vx * nx + b.vy * ny

                    // Скорости вдоль нормали после столкновения (для упругого удара шаров одинаковой массы они меняются местами)
                    val va_n_final = vb_n
                    val vb_n_final = va_n

                    // Вычитаем старую проекцию на нормаль и добавляем новую
                    a.vx += (va_n_final - va_n) * nx
                    a.vy += (va_n_final - va_n) * ny
                    b.vx += (vb_n_final - vb_n) * nx
                    b.vy += (vb_n_final - vb_n) * ny

                    // Если шары перекрываются, их нужно немного раздвинуть вдоль линии столкновения.
                    val overlap = (a.r + b.r - dist) / 2 // Величина наложения (делим на 2, т.к. двигаем оба шара)
                    a.x -= overlap * nx // Двигаем шар 'a' против направления столкновения
                    a.y -= overlap * ny
                    b.x += overlap * nx // Двигаем шар 'b' по направлению столкновения
                    b.y += overlap * ny
                }
            }
        }
    }

    /**
     * Проверяет, попал ли какой-либо из шаров в лузу.
     * Если шар попал, он удаляется со стола (кроме битка, который возвращается на позицию).
     */
    private fun checkPockets() {
        val iterator = balls.iterator() // Используем итератор для безопасного удаления элементов из списка во время итерации
        while (iterator.hasNext()) { // Пока есть следующий шар в списке
            val ball = iterator.next() // Получаем текущий шар
            for (p in pockets) { // Проверяем каждую лузу
                val dx = ball.x - p.x // Разница по X между шаром и центром лузы
                val dy = ball.y - p.y // Разница по Y
                val dist = sqrt(dx * dx + dy * dy) // Расстояние между шаром и центром лузы

                // Если расстояние меньше суммы радиусов шара и лузы,
                // для упрощения считаем, что шар попадает, если его центр достаточно близко к центру лузы.
                if (dist < p.r) { // Шар считается забитым, если его центр попадает в радиус лузы
                    if (ball.number == 0) { // Если это белый шар
                        // Возвращаем белый шар на начальную позицию или специальную точку
                        ball.x = width / 2f
                        ball.y = height - 200f
                        ball.vx = 0f // Обнуляем скорость
                        ball.vy = 0f
                    } else { // Если это любой другой шар
                        iterator.remove() // Удаляем шар из списка (забит)
                    }
                    break // Выходим из цикла по лузам, так как шар уже обработан
                }
            }
        }
    }

    /**
     * Возвращает цвет шара в зависимости от его номера.
     * @param number Номер шара.
     * @return Целочисленное значение цвета (Color.XXX).
     */
    private fun getBallColor(number: Int): Int {
        return when (number) {
            1, 9 -> Color.YELLOW // Желтый
            2, 10 -> Color.BLUE // Синий
            3, 11 -> Color.RED // Красный
            4, 12 -> Color.rgb(128, 0, 128) // Фиолетовый
            5, 13 -> Color.rgb(255, 165, 0) // Оранжевый
            6, 14 -> Color.GREEN // Зеленый
            7, 15 -> Color.MAGENTA // Малиновый
            8 -> Color.BLACK // Черный
            0 -> Color.WHITE // Белый
            else -> Color.GRAY
        }
    }

    /**
     * Внутренний data-класс для представления лузы.
     * @param x X-координата центра лузы.
     * @param y Y-координата центра лузы.
     * @param r Радиус лузы.
     */
    data class Pocket(val x: Float, val y: Float, val r: Float)
}

