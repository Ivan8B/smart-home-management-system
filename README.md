## Список устройств и их настройки

### Шлюз modbus-tcp

**Модель** - USR-DR302, документация в docs/USR-DR302.

**Настройки**:

* включен DHCP клиент
* в разделе "Serial Port": Baud Rate"="9600", "Data Size" = "8 bit", "Parity Mode" = "None", "Stop Bits" = "1", "Local
  Port Number" = "8234", "Work Mode" = "TCP Server"
* включен "Modbus Gateway" (в разделе Expand Function)
* отключена опция "TCP Server-kick off old connection" (в разделе Expand Function)

Нужно прописать в /etc/hosts связку hostname-ip ds302 и заполнить в application.yml параметры modbus.tcpHost(из
/etc/hosts) и modbus.tcpPort(параметр "Local Port Number" на DR302).

**Подключения**:

* по MODBUS шине подключены все устройства ниже

### Плата сбора температур с датчиков DS18B20 N1

**Модель** - R4DCB08, документация в docs/R4DCB08

**Настройки**

* выставлен адрес 1 командой FF 06 00 FE 00 01 3C 24
* адрес указан в параметре "temperatureSensorsBoard.board1.address"

**Подключения**:

* к входам GND/D/5V подключены температурные датчики DS18B20, регистры начинаются с 0 и прописываются в enum
  TemperatureSensor

### Реле CHINALCTECH (bestep) 2IN-2OUT N1

Реле управления газовым котлом и питанием сервопривода теплого пола.

**Модель** - неизвестна, документация в docs/CHINALTECH_2IN-2OUT_RELAY

**Настройки**:

* выставлен адрес 2 командой 00 10 00 00 00 01 02 00 02 2A 01
* адрес указан в параметрах "gasBoiler.relay.address", "floorHeating.valve.relay.address"

**Подключения**:

* к COM1 и NС1 подключен выход комнатного термостата газового котла (нормально открытый), параметр "
  gasBoiler.relay.coil=0"
* к COM2 и NO2 подключено питание сервопривода теплых полов, параметр "floorHeating.valve.relay.coil=1"

**NOTE**
Газовый котел подключен к нормально закрытым выходам реле, то есть при отключении реле его цепь замкнута.
Это сделано для того, чтобы при отказе системы управления повысить вероятного того, что он останется включенным.
Управление реле инвертировано.

### Реле CHINALCTECH (bestep) 2IN-2OUT N2

Реле управления электрическим котлом и насосами отопления.

**Модель** - неизвестна, документация в docs/CHINALTECH_2IN-2OUT_RELAY

**Настройки**:

* выставлен адрес 3 командой 00 10 00 00 00 01 02 00 03 EB C1
* адрес указан в параметрах "electricBoiler.relay.address", "pump.relay.address"

**Подключения**:

* к COM1 и NO1 подключен выход комнатного термостата электрического котла (нормально открытый), параметр "
  electricBoiler.relay.coil=0"
* к COM2 и NC2 подключен насос теплых полов и насос радиатора, параметр "pump.relay.coil=1"

**NOTE**
Насосы теплых полов и радиаторов подключены к нормально закрытым выходам реле, то есть при отключении реле его цепь
замкнута.
Это сделано для того, чтобы при отказе системы управления повысить вероятного того, что он останется включенным.
Управление реле инвертировано.

### Реле CHINALCTECH (bestep) 2IN-2OUT N3

Реле управления обманкой уличного датчика газового котла.

**Модель** - неизвестна, документация в docs/CHINALTECH_2IN-2OUT_RELAY

**Настройки**:

* выставлен адрес 4 командой 00 10 00 00 00 01 02 00 04 AA 03
* адрес указан в параметрах "pump.relay.address", "floorHeating.valve.relay.address"

**Подключения**:

* к COM1 и NC1 подключен насос теплых полов и насос радиатора, параметр "pump.relay.coil=0"
* к COM2 и NO2 подключено питание сервопривода теплых полов, параметр "floorHeating.valve.relay.coil=1"

### Цифро-аналоговой преобразователь N4DAC02

Цифро-аналоговый преобразователь (DAC), управляющий положением клапана подмеса теплого пола.

**Модель** - N4DAC02, документация в docs/N4DAC02

**Настройки**:

* выставлен адрес 5 командой 01 06 00 0E 00 05 28 0A (предполагаем, что устройство с завода доступно по адресу 01, 
  если это не так - нужно узнать адрес устройства, см. документацию)
* адрес указан в параметрах "floorHeating.valve.dac.address"

**Подключения**:

* к O2 и GND подключен сервопривод управления подмесом в теплые полы

### Сервопривод управления подмесом в теплые полы ESBE

**Модель** - ARA-659, документация в docs/ARA-659.
Управляется через сигнал 2-10В с ЦАП N4DAC02, питается через реле CHINALCTECH (bestep) 2IN-2OUT N1.
Движение против часовой стрелке (CCW), при напряжении 2В горячая вода из котла в теплые полы не подмешивается.
Время поворота выставлено на 45 секунд (по факту 52), на это время подается питание (параметр "floorHeating.valve.relay.delay").
Все переключатели кроме третьего выставлены в положение "включено" (включено движение против часовой стрелке, работа по напряжению 2-10В).

### Универсальные датчики состояния воздуха

Универсальный датчик, измеряющий температуру, влажность, количество CO2 в воздухе. В системе 4 шт, расположены в 
жилых комнатах.

**Модель** - CO2RS485, документация в docs/CO2RS485

**Настройки**:
Предполагаем, что устройство с завода доступно по адресу 01, если это не так - нужно узнать адрес устройства, см. документацию
* на каждом датчике выставлена скорость подключения 9600 командой 01 06 07 D1 00 02 59 46
* выставлены адреса 7, 8, 9, 10 командами 01 06 07 D0 00 07 С8 85, 01 06 07 D0 00 08 88 81, 01 06 07 D0 00 09 49 41, 
  01 06 07 D0 00 0A 09 40
* адреса указаны в параметрах "livingRooms.[].universalSensor.address"