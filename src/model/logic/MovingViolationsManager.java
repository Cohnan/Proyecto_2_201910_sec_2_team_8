package model.logic;

import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;

import javax.management.Query;

import com.opencsv.CSVReader;

import model.data_structures.*;
import model.util.Sort;
import model.vo.Coordenadas;
import model.vo.EstadisticaInfracciones;
import model.vo.EstadisticasCargaInfracciones;
import model.vo.InfraccionesFecha;
import model.vo.InfraccionesFechaHora;
import model.vo.InfraccionesFranjaHoraria;
import model.vo.InfraccionesFranjaHorariaViolationCode;
import model.vo.InfraccionesLocalizacion;
import model.vo.InfraccionesViolationCode;
import model.vo.VOColeccion;
import model.vo.VOMovingViolations;

public class MovingViolationsManager {

	//TODO Definir atributos necesarios

	/**
	 * Lista donde se van a cargar los datos de los archivos
	 */
	private static IArregloDinamico<VOMovingViolations> movingVOLista;

	/**
	 * Numero actual del semestre cargado
	 */
	private static int semestreCargado = -1;

	/**
	 * Numero infracciones cargadas
	 */
	private static int nInfraccionesCargadas = -1;

	/**
	 * X minimo de infraccion
	 */
	private static double xMin;
	/**
	 * Y minimo de infraccion
	 */
	private static double yMin;
	/**
	 * X maximo de infraccion
	 */
	private static double xMax;
	/**
	 * Y maximo de infraccion
	 */
	private static double yMax;

	/*
	 * Creados en Parte A
	 */
	/**
	 * 1A
	 */
	private IColaPrioridad<InfraccionesFranjaHoraria> cpFranjasHorarias;

	/**
	 * 2A
	 */
	private LinProbTH<Coordenadas, InfraccionesLocalizacion> thLocalizaciones; // Puede que se cambie a TablaSimOrd, pues Coordenadas tiene un orden que mencionan en el enunciado

	/**
	 * 3A
	 */
	private ITablaSimOrd<LocalDate, InfraccionesFecha> abFechas;



	/**
	 * 1B
	 */
	private IColaPrioridad<InfraccionesViolationCode> cpViolationCode;


	/**
	 * 2B
	 */
	private ITablaSimOrd<Coordenadas,InfraccionesLocalizacion> abLocalizaciones;


	/**
	 * 3B
	 */
	private ITablaSimOrd<Double,InfraccionesFechaHora> abValorAcumulado;


	/**
	 * 1C
	 */
	private LinProbTH<Integer, InfraccionesLocalizacion> thLocAddress;

	/**
	 * 2C
	 */

	private ITablaHash<LocalTime, InfraccionesFranjaHorariaViolationCode> thFranjaCode;
	
	/**
	 * 3C
	 */
	private IColaPrioridad<InfraccionesLocalizacion> cpLocalizaciones;

	/**
	 * Metodo constructor
	 */
	public MovingViolationsManager()
	{

	}



	/**
	 * Carga los datos del semestre dado
	 * @param n Numero del semestre del anio (1 � 2)
	 * @return Cola con el numero de datos cargados por mes del semestre
	 */
	public EstadisticasCargaInfracciones loadMovingViolations(int n)
	{
		EstadisticasCargaInfracciones numeroDeCargas;
		if(n == 1)
		{
			numeroDeCargas = loadMovingViolations(new String[] {"Moving_Violations_Issued_in_January_2018.csv", 
					"Moving_Violations_Issued_in_February_2018.csv",
					//					"Moving_Violations_Issued_in_March_2018.csv",
					//					"Moving_Violations_Issued_in_April_2018.csv",
					//					"Moving_Violations_Issued_in_May_2018.csv",
					//					"Moving_Violations_Issued_in_June_2018.csv"
			});
			semestreCargado = 1;
		}
		else if(n == 2)
		{
			numeroDeCargas = loadMovingViolations(new String[] {"Moving_Violations_Issued_in_July_2018.csv",
					"Moving_Violations_Issued_in_August_2018.csv",
					//					"Moving_Violations_Issued_in_September_2018.csv", 
					//					"Moving_Violations_Issued_in_October_2018.csv",
					//					"Moving_Violations_Issued_in_November_2018.csv",
					//					"Moving_Violations_Issued_in_December_2018.csv"
			});
			semestreCargado = 2;
		}
		else
		{
			throw new IllegalArgumentException("No existe ese semestre en un annio.");
		}
		return numeroDeCargas;
	}

	/**
	 * Metodo ayudante
	 * Carga la informacion sobre infracciones de los archivos a una pila y una cola ordenadas por fecha.
	 * Dado un arreglo con los nombres de los archivos a cargar
	 * @returns Cola con el numero de datos cargados por mes del cuatrimestre
	 */
	private EstadisticasCargaInfracciones loadMovingViolations(String[] movingViolationsFilePaths){
		CSVReader reader = null;

		int totalInf = 0;
		int contadorInf; // Cuenta numero de infracciones en cada archivo
		int nMeses = movingViolationsFilePaths.length;
		int[] infPorMes = new int[nMeses];

		try {
			movingVOLista = new ArregloDinamico<VOMovingViolations>(670000);

			int nArchivoActual = 0;
			for (String filePath : movingViolationsFilePaths) {
				reader = new CSVReader(new FileReader("data/"+filePath));

				contadorInf = 0;
				// Deduce las posiciones de las columnas que se reconocen de acuerdo al header
				String[] headers = reader.readNext();
				int[] posiciones = new int[VOMovingViolations.EXPECTEDHEADERS.length];
				for (int i = 0; i < VOMovingViolations.EXPECTEDHEADERS.length; i++) {
					posiciones[i] = buscarArray(headers, VOMovingViolations.EXPECTEDHEADERS[i]);
				}

				// Lee linea a linea el archivo para crear las infracciones y cargarlas a la lista
				VOMovingViolations infraccion;
				for (String[] row : reader) {
					infraccion = new VOMovingViolations(posiciones, row);
					movingVOLista.agregar(infraccion);
					contadorInf += 1;
					if(xMin<=0 || yMin<=0){
						xMin= infraccion.getXCoord();
						yMin = infraccion.getYCoord();
					}

					// Se actualizan las coordenadas extremas
					xMin = Math.min(xMin, infraccion.getXCoord());
					xMax = Math.max(xMax, infraccion.getXCoord());
					yMin = Math.min(yMin, infraccion.getYCoord());
					yMax = Math.max(yMax, infraccion.getYCoord());			
				}
				// Se agrega el numero de infracciones cargadas en este archivo al resultado 
				totalInf += contadorInf;
				infPorMes[nArchivoActual++] = contadorInf;
			}
			nInfraccionesCargadas = totalInf;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
					return null;
				}
			}
		}
		return new EstadisticasCargaInfracciones(totalInf, nMeses, infPorMes, new double[] {xMin, yMin, xMax, yMax});
	}


	/*
	 * Parte A 
	 */
	/**
	 * Requerimiento 1A: Obtener el ranking de las N franjas horarias
	 * que tengan m�s infracciones. 
	 * @param int N: N�mero de franjas horarias que tienen m�s infracciones
	 * @return Cola con objetos InfraccionesFranjaHoraria
	 */
	public IQueue<InfraccionesFranjaHoraria> rankingNFranjas(int M)
	{
		IQueue<InfraccionesFranjaHoraria> mPrimeras = new Queue<InfraccionesFranjaHoraria>();

		// Crear estructura con la informacion de las 24 franjas horarias
		if (cpFranjasHorarias == null) {
			cpFranjasHorarias = new MaxHeapCP<InfraccionesFranjaHoraria>();

			// Se deben las estadisticas completas para cada franja horaria antes de crear la cola de prioridad
			// pues se necesita saber la prioridad final de cada elemento a agregar
			Sort.ordenarShellSort(movingVOLista, new VOMovingViolations.FranjaHorariaOrder());

			Iterator<VOMovingViolations> iterador = movingVOLista.iterator();

			// Si no hay datos, entonces retorna una cola vacia
			if (!iterador.hasNext()) return mPrimeras;

			// Como los datos estan ordenados, tomamos una infraccion de referencia para comparar con
			// los datos inmediatamente siguientes
			VOMovingViolations infrRevisar = iterador.next();
			int horaRef = infrRevisar.getTicketIssueDate().getHour() % 24;

			// Datos a actualizar y finalmente agregar
			InfraccionesFranjaHoraria voFranja = new InfraccionesFranjaHoraria(LocalTime.of(horaRef, 0, 0), 
					LocalTime.of(horaRef, 59, 59));
			voFranja.agregarEstadistica(infrRevisar);

			while (iterador.hasNext()) {
				infrRevisar = iterador.next();

				if (horaRef == infrRevisar.getTicketIssueDate().getHour() % 24) {
					// Actualiza datos actuales
					voFranja.agregarEstadistica(infrRevisar);
				} else {
					// Agrega el dato que se estuvo actualizando a la cola
					cpFranjasHorarias.agregar(voFranja);
					// Reestablecer referencias
					horaRef = infrRevisar.getTicketIssueDate().getHour() % 24;
					// Reestablecer datos a actualizar
					voFranja = new InfraccionesFranjaHoraria(LocalTime.of(horaRef, 0, 0), 
							LocalTime.of(horaRef, 59, 59));
					voFranja.agregarEstadistica(infrRevisar);
				}
			}
			// Agregar la ultima referencia
			cpFranjasHorarias.agregar(voFranja);
		}

		int i = 0;
		for (InfraccionesFranjaHoraria estadistica : cpFranjasHorarias) {
			if (++i > M) break;
			mPrimeras.enqueue(estadistica);
		}

		return mPrimeras;		
	}

	/**
	 * Requerimiento 2A: Consultar  las  infracciones  por
	 * Localizaci�n  Geogr�fica  (Xcoord, Ycoord) en Tabla Hash.
	 * @param  double xCoord : Coordenada X de la localizacion de la infracci�n
	 *			double yCoord : Coordenada Y de la localizacion de la infracci�n
	 * @return Objeto InfraccionesLocalizacion
	 */
	public InfraccionesLocalizacion consultarPorLocalizacionHash(double xCoord, double yCoord)
	{	
		// En caso de no existir ya la tabla de hash con la informacion, la crea
		if(thLocalizaciones == null) {
			crearThLocalizaciones();
		}
		// Retorna la informacion deseada desde una tabla de hash
		return thLocalizaciones.get(new Coordenadas(xCoord, yCoord));		
	}

	private void crearThLocalizaciones() {
		thLocalizaciones = new LinProbTH<Coordenadas, InfraccionesLocalizacion>(4);

		// Revisa la lista de infracciones una a una, actualizando las estadisticas que corresponden 
		// a la coordenada actual
		Coordenadas curCoord;
		InfraccionesLocalizacion locActual;

		for (VOMovingViolations infraccion : movingVOLista) {
			curCoord = new Coordenadas(infraccion.getXCoord(), infraccion.getYCoord());
			locActual = thLocalizaciones.get(curCoord);

			// Si la coordenada actual aun no esta en la tabla, la crea
			if (locActual == null) locActual = new InfraccionesLocalizacion(infraccion.getXCoord(), infraccion.getYCoord(), infraccion.getLocation(), infraccion.getAddressID(), infraccion.getStreetsegID());

			// Actualiza la estadistica correspondiente a esta coordenada
			locActual.agregarEstadistica(infraccion);
			thLocalizaciones.put(curCoord, locActual);
		}
	}

	/**
	 * Requerimiento 3A: Buscar las infracciones por rango de fechas
	 * @param  LocalDate fechaInicial: Fecha inicial del rango de b�squeda
	 * 		LocalDate fechaFinal: Fecha final del rango de b�squeda
	 * @return Cola con objetos InfraccionesFecha
	 */
	public IQueue<InfraccionesFecha> consultarInfraccionesPorRangoFechas(LocalDate fechaInicial, LocalDate fechaFinal)
	{
		// En caso de no existir ya la tabla de hash con la informacion, la crea
		if(abFechas == null) {
			abFechas = new BlancoRojoBST<LocalDate, InfraccionesFecha>();

			// Revisa la lista de infracciones una a una, actualizando las estadisticas que corresponden 
			// a la coordenada actual
			LocalDate fechaAct;
			InfraccionesFecha estadAct;

			for (VOMovingViolations infraccion : movingVOLista) {
				fechaAct = infraccion.getTicketIssueDate().toLocalDate();
				estadAct = abFechas.get(fechaAct);

				// Si la coordenada actual aun no esta en la tabla, la crea
				if (estadAct == null) estadAct = new InfraccionesFecha(fechaAct);

				// Actualiza la estadistica correspondiente a esta coordenada
				estadAct.agregarEstadistica(infraccion);
				//System.out.println(estadAct);
				abFechas.put(fechaAct, estadAct);
			}
		}
		// Retorna la informacion deseada desde una tabla de hash
		IQueue<InfraccionesFecha> respuesta = new Queue<InfraccionesFecha> ();
		for (InfraccionesFecha estadistica : abFechas.valuesInRange(fechaInicial, fechaFinal)) { 
			respuesta.enqueue(estadistica);
		}

		return respuesta;
	}

	/**
	 * Requerimiento 1B: Obtener  el  ranking  de  las  N  tipos  de  infracci�n
	 * (ViolationCode)  que  tengan  m�s infracciones.
	 * @param  int N: Numero de los tipos de ViolationCode con m�s infracciones.
	 * @return Cola con objetos InfraccionesViolationCode con top N infracciones
	 */
	public IQueue<InfraccionesViolationCode> rankingNViolationCodes(int N)
	{
		IQueue<InfraccionesViolationCode> resultado = new Queue<InfraccionesViolationCode>();

		//Verifica si los datos ya fueron cargados anteriormente
		if (cpViolationCode == null) {
			cpViolationCode = new MaxHeapCP<InfraccionesViolationCode>();

			crearCpViolationCode();
		}	


		//Selecciona las N primeras infraccionesViolationCode
		int i = 0;
		for (InfraccionesViolationCode act:cpViolationCode) {
			if(++i>N) break;
			resultado.enqueue(act);
		}

		//Devuelve el resultado
		return resultado;

	}

	private void crearCpViolationCode() {
		//Se ordenan por ViolationCode Order para poder crear las estad�sticas
		Sort.ordenarShellSort(movingVOLista, new VOMovingViolations.ViolationCodeOrder());
		Iterator<VOMovingViolations> iterador = movingVOLista.iterator();

		// Si no hay datos, entonces retorna una cola vacia
		if (!iterador.hasNext()) return ;


		//Se recorren las infracciones tomando la referencia de su violationCode
		VOMovingViolations infrRevisar = iterador.next();
		String violationCodeActual = infrRevisar.getViolationCode();

		InfraccionesViolationCode voViolation = new InfraccionesViolationCode(violationCodeActual);
		voViolation.agregarEstadistica(infrRevisar);

		while(iterador.hasNext()){
			infrRevisar = iterador.next();

			//Si tienen el mismo VOCode, van en la misma estad�stica
			if(violationCodeActual.equals(infrRevisar.getViolationCode())){
				voViolation.agregarEstadistica(infrRevisar);
			}
			else{

				//Si no, se agrega al MAXHEAP y se reincia
				cpViolationCode.agregar(voViolation);
				violationCodeActual = infrRevisar.getViolationCode();
				voViolation = new InfraccionesViolationCode(violationCodeActual);
				voViolation.agregarEstadistica(infrRevisar);
			}
		}

		//Para agregar la �ltima referencia
		cpViolationCode.agregar(voViolation);
	}

	/**
	 * Requerimiento 2B: Consultar las  infracciones  por  
	 * Localizaci�n  Geogr�fica  (Xcoord, Ycoord) en Arbol.
	 * @param  double xCoord : Coordenada X de la localizacion de la infracci�n
	 *			double yCoord : Coordenada Y de la localizacion de la infracci�n
	 * @return Objeto InfraccionesLocalizacion
	 */
	public InfraccionesLocalizacion consultarPorLocalizacionArbol(double xCoord, double yCoord)
	{
		//Verifica si ya estan cargados los datos necesarios
		if(abLocalizaciones == null) {
			crearAbLocalizaciones();
		}
		//Se retorna la localizaci�n buscada
		return abLocalizaciones.get(new Coordenadas(xCoord, yCoord));		
	}


	private void crearAbLocalizaciones(){

		boolean primera = true;
		int contador = 1;
		abLocalizaciones = new BlancoRojoBST<Coordenadas, InfraccionesLocalizacion>();
		Coordenadas auxiliar;
		InfraccionesLocalizacion locActual;

		//Se recorre la lista de las infracciones
		for (VOMovingViolations infraccion : movingVOLista) {
			auxiliar = new Coordenadas(infraccion.getXCoord(), infraccion.getYCoord());
			locActual = abLocalizaciones.get(auxiliar);
			//Si  la localizaci�n actual no existe, se crea una nueva InfraccionesLocalizaci�n
			if (locActual == null) locActual = new InfraccionesLocalizacion(infraccion.getXCoord(), infraccion.getYCoord(), infraccion.getLocation(), infraccion.getAddressID(), infraccion.getStreetsegID());
			//Se agrega a la estad�stica la infracci�n actual
			locActual.agregarEstadistica(infraccion);
			abLocalizaciones.put(auxiliar, locActual);
		}

	}

	/**
	 * Requerimiento 3B: Buscar las franjas de fecha-hora donde se tiene un valor acumulado
	 * de infracciones en un rango dado [US$ valor inicial, US$ valor final]. 
	 * @param  double valorInicial: Valor m�nimo acumulado de las infracciones
	 * 		double valorFinal: Valor m�ximo acumulado de las infracciones.
	 * @return Cola con objetos InfraccionesFechaHora
	 */
	public IQueue<InfraccionesFechaHora> consultarFranjasAcumuladoEnRango(double valorInicial, double valorFinal)
	{

		//La cola que contiene la respuesta
		IQueue<InfraccionesFechaHora> respuesta = new Queue<>();

		//Si no se ha cargado el �rbol, se construye
		if(abValorAcumulado == null){
			crearabValorAcumulado();
		}


		//Se verifica, para cada uno de los valores, si esta en el rango o no
		for(double s: abValorAcumulado){
			//Si esta en el rango se a�ade a la cola
			if(s>=valorInicial && s<=valorFinal){
				respuesta.enqueue(abValorAcumulado.get(s));
			}
		}

		return respuesta;

	}


	private void crearabValorAcumulado(){

		//Se ordenan por TicketIssueOrder Order para poder crear las estad�sticas
		Sort.ordenarShellSort(movingVOLista, new VOMovingViolations.TicketIssueOrder());

		//Se recorren todas las infracciones ya ordenadas
		Iterator<VOMovingViolations> iterador = movingVOLista.iterator();
		abValorAcumulado = new BlancoRojoBST<Double, InfraccionesFechaHora>();

		// Si no hay datos, entonces retorna una cola vacia
		if (!iterador.hasNext()) return ;



		VOMovingViolations infrRevisar = iterador.next();
		LocalDateTime fechaHoraActual = infrRevisar.getTicketIssueDate();
		LocalDateTime inicio;
		//Dependiendo del semestre, se empieza la hora min y max
		if(darNumeroSemestre() == 1){
			inicio = LocalDateTime.of(2018,1,1,0, 0, 0);
		}else{
			inicio = LocalDateTime.of(2018,7,1,0, 0, 0);
		}

		//Esta por franjas de una hora o de 59:59 minutos
		LocalDateTime fin = inicio.plusMinutes(59);
		fin = fin.plusSeconds(59);


		InfraccionesFechaHora voViolation = new InfraccionesFechaHora(inicio, fin);
		//		voViolation.agregarEstadistica(infrRevisar);

		while(infrRevisar!=null){

			fechaHoraActual = infrRevisar.getTicketIssueDate();
			boolean agrego = false;
			//Se recorren las fechas hasta que se agregue la infraccion
			while(!agrego){

				//Si esta en el rango de fechas se agrega a la estad�stica
				if(fechaHoraActual.compareTo(inicio)>=0 && fechaHoraActual.compareTo(fin)<=0){
					voViolation.agregarEstadistica(infrRevisar);
					agrego = true;

				}
				else{

					//Si no, se agrega al arbol y se reincia
					abValorAcumulado.put(voViolation.getValorTotal(), voViolation);
					if(inicio.getHour()<23){
						inicio = inicio.plusHours(01);
					}
					else{
						inicio = inicio.plusDays(1);
						inicio = inicio.minusHours(23);
					}
					fin = inicio.plusMinutes(59);
					fin = fin.plusSeconds(59);

					//Se crea la nueva estad�stica
					voViolation = new InfraccionesFechaHora(inicio, fin);
				}
			}

			//Se pasa a la siguiente infracci�n
			infrRevisar = iterador.next();
		}

	}

	/**
	 * Requerimiento 1C: Obtener  la informaci�n de  una  addressId dada
	 * @param  int addressID: Localizaci�n de la consulta.
	 * @return Objeto InfraccionesLocalizacion
	 */
	public InfraccionesLocalizacion consultarPorAddressId(int addressID)
	{
		// En caso de no existir ya la tabla de hash con la informacion, la crea
		if(thLocAddress == null) {
			thLocAddress = new LinProbTH<Integer, InfraccionesLocalizacion>(4); //TODO check that the maxloadfactor is loooow

			if (thLocalizaciones == null && abLocalizaciones == null) {
				crearThLocAdd();

				// En caso de que thLocalizaciones o abLocalizaciones ya existan
			} else if(thLocalizaciones != null) {
				InfraccionesLocalizacion localizacion;
				for (Coordenadas coordenada : thLocalizaciones) {
					localizacion = thLocalizaciones.get(coordenada);
					thLocAddress.put(localizacion.getAdressID(), localizacion);
				}
			} else {
				InfraccionesLocalizacion localizacion;
				for (Coordenadas coordenada : abLocalizaciones) {
					localizacion = abLocalizaciones.get(coordenada);
					thLocAddress.put(localizacion.getAdressID(), localizacion);
				}
			}
		}
		// Retorna la informacion deseada desde una tabla de hash
		return thLocAddress.get(addressID);
	}

	private void crearThLocAdd() {
		// Revisa la lista de infracciones una a una, actualizando las estadisticas que corresponden 
		// a la coordenada actual
		Integer adressAct;
		InfraccionesLocalizacion estadisticaAct;

		for (VOMovingViolations infraccion : movingVOLista) {
			adressAct = infraccion.getAddressID();
			estadisticaAct = thLocAddress.get(adressAct);

			// Si la coordenada actual aun no esta en la tabla, la crea
			if (estadisticaAct == null) estadisticaAct = new InfraccionesLocalizacion(infraccion.getXCoord(), infraccion.getYCoord(), infraccion.getLocation(), infraccion.getAddressID(), infraccion.getStreetsegID());

			// Actualiza la estadistica correspondiente a esta coordenada
			estadisticaAct.agregarEstadistica(infraccion);
			thLocAddress.put(adressAct, estadisticaAct);
		}
	}

	/**
	 * Requerimiento 2C: Obtener  las infracciones  en  un  rango de
	 * horas  [HH:MM:SS  inicial,HH:MM:SS  final]
	 * @param  LocalTime horaInicial: Hora  inicial del rango de b�squeda
	 * 		LocalTime horaFinal: Hora final del rango de b�squeda
	 * @return Objeto InfraccionesFranjaHorariaViolationCode
	 */
	public InfraccionesFranjaHorariaViolationCode consultarPorRangoHoras(LocalTime horaInicial, LocalTime horaFinal)
	{
		if (thFranjaCode == null) {

		}

		return null;
		Sort.ordenarShellSort(movingVOLista, new VOMovingViolations.FranjaHorariaOrder()); // TODO puedo eliminar este sorting?
		
		
		InfraccionesFranjaHorariaViolationCode acumulado = thFranjaCode.get(horaFinal);
		InfraccionesFranjaHorariaViolationCode aRestar = thFranjaCode.get(horaFinal.equals(LocalTime.of(0, 0))? LocalTime.of(0, 0) : horaFinal.minusSeconds(1));
		
		return acumulado.eliminarEstadisticas(aRestar);
	}

	/**
	 * Requerimiento 3C: Obtener  el  ranking  de  las  N localizaciones geogr�ficas
	 * (Xcoord,  Ycoord)  con  la mayor  cantidad  de  infracciones.
	 * @param  int N: Numero de las localizaciones con mayor n�mero de infracciones
	 * @return Cola de objetos InfraccionesLocalizacion
	 */
	public IQueue<InfraccionesLocalizacion> rankingNLocalizaciones(int N)
	{		
		if (cpLocalizaciones == null) {
			cpLocalizaciones = new MaxHeapCP<InfraccionesLocalizacion>();
			Iterable<InfraccionesLocalizacion> iterable;

			if (thLocAddress != null) { // Primera opcion pues es una estructura usada en la parte C tambien
				iterable = thLocAddress.iteratorValues();

			} else if (thLocalizaciones != null) { // Segunda opcion pues la tabla de hash es mas rapida que un arbol balanceado
				iterable =  thLocalizaciones.iteratorValues();

			} else if (abLocalizaciones != null) {
				iterable = abLocalizaciones.valuesInRange(abLocalizaciones.min(), abLocalizaciones.max());

			} else { // Si ninguna estructura con la informacion deseada se encuentra, crea la estructura del 1C
				thLocAddress = new LinProbTH<Integer, InfraccionesLocalizacion>(4);
				crearThLocAdd();

				iterable =  thLocAddress.iteratorValues();
			}

			// Itera sobre los valores de la estructura seleccionada para crear la cola de prioridad completa
			for (InfraccionesLocalizacion estadistica : iterable) cpLocalizaciones.agregar(estadistica);
		}

		// Se eligen los primeros N valores de la cola de prioridad
		IQueue<InfraccionesLocalizacion> rankingN = new Queue<>();

		int i = 0;
		for (InfraccionesLocalizacion estadistica : cpLocalizaciones) {
			if(i++ >= N) break;
			rankingN.enqueue(estadistica);
		}

		return rankingN;
	}

	/**
	 * Requerimiento 4C: Obtener la  informaci�n  de  los c�digos (ViolationCode) ordenados por su numero de infracciones.
	 * @return Contenedora de objetos InfraccionesViolationCode.
	 */
	public IColaPrioridad<InfraccionesViolationCode> ordenarCodigosPorNumeroInfracciones()
	{
		if (cpViolationCode == null) {
			cpViolationCode = new MaxHeapCP<InfraccionesViolationCode>();
			crearCpViolationCode();
		}
		return cpViolationCode;		
	}

	public int darNumeroInfraccionesCargadas() {
		return nInfraccionesCargadas;
	}

	public int darNumeroSemestre() {
		return semestreCargado;
	}

	/*
	 * Metodos ayudantes 
	 */
	/**
	 * Metodo para buscar strings en un array de strings, usado para deducir la posicion
	 * de las columnas esperadas en cada archivo.
	 * @param array
	 * @param string
	 * @return
	 */
	private int buscarArray(String[] array, String string) {
		int i = 0;

		while (i < array.length) {
			if (array[i].equalsIgnoreCase(string)) return i;
			i += 1;
		}
		return -1;
	}
}
