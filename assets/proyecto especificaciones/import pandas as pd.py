import pandas as pd

# Data 1: Actividades y Aliados por Temas
data_actividades = [
    {
        "Día": "Día 1",
        "Hora": "09:30 - 10:15",
        "Actividad / Tema": "Tecnologías emergentes: IA y Blockchain en acción",
        "Eje": "Tech",
        "Capítulo Técnico IEEE": "CIS (Jorge Párraga), ComSoc (Román Lara), CS",
        "Grupo No Técnico / Afinidad": "SSIT, SIGHT",
        "Empresa / Aliado Externo": "Ethereum Ecuador (ETH EC), GDG Ecuador",
        "Rol del Aliado / Notas": "Conferencista, Mentores IA/Blockchain"
    },
    {
        "Día": "Día 1",
        "Hora": "10:15 - 11:00",
        "Actividad / Tema": "Green IT: Desafíos y oportunidades sostenibles",
        "Eje": "Tech",
        "Capítulo Técnico IEEE": "ComSoc (Román Lara), IES (Mario - UPS Quito), IAS",
        "Grupo No Técnico / Afinidad": "SSIT, SIGHT, YP",
        "Empresa / Aliado Externo": "CONQUITO, Empresas Cloud/Infraestructura",
        "Rol del Aliado / Notas": "Ponente en TI verde y eficiencia energética"
    },
    {
        "Día": "Día 1",
        "Hora": "11:15 - 12:00",
        "Actividad / Tema": "PYMEs Verdes: IA y Blockchain en la sostenibilidad empresarial",
        "Eje": "Engage",
        "Capítulo Técnico IEEE": "IAS, IES (Mario), TEMS, CIS",
        "Grupo No Técnico / Afinidad": "YP",
        "Empresa / Aliado Externo": "Cámara de Comercio de Quito, CONQUITO, ETH EC",
        "Rol del Aliado / Notas": "Charla sobre aplicación real en negocios"
    },
    {
        "Día": "Día 1",
        "Hora": "12:00 - 12:45",
        "Actividad / Tema": "Habilidades blandas en la era digital",
        "Eje": "Train",
        "Capítulo Técnico IEEE": "TEMS",
        "Grupo No Técnico / Afinidad": "YP, WIE",
        "Empresa / Aliado Externo": "Consultoras de RRHH / Reclutamiento Tech",
        "Rol del Aliado / Notas": "Taller/Charla sobre empleabilidad y soft skills"
    },
    {
        "Día": "Día 1",
        "Hora": "14:00 - 15:00",
        "Actividad / Tema": "Taller: Design Thinking para liderazgo y comunicación",
        "Eje": "Train / Lead",
        "Capítulo Técnico IEEE": "TEMS",
        "Grupo No Técnico / Afinidad": "YP, WIE, SAC",
        "Empresa / Aliado Externo": "Hubs de Innovación (Impaqto, KrugerLabs)",
        "Rol del Aliado / Notas": "Facilitador de taller interactivo"
    },
    {
        "Día": "Día 1",
        "Hora": "15:00 - 15:45",
        "Actividad / Tema": "Psicología positiva en el trabajo: Equipos felices y productivos",
        "Eje": "Train",
        "Capítulo Técnico IEEE": "N/A",
        "Grupo No Técnico / Afinidad": "WIE, YP",
        "Empresa / Aliado Externo": "Consultores de Desarrollo Organizacional / Salud Mental",
        "Rol del Aliado / Notas": "Speaker en psicología organizacional"
    },
    {
        "Día": "Día 1",
        "Hora": "15:45 - 16:30",
        "Actividad / Tema": "Blockchain para la economía circular: Trazabilidad",
        "Eje": "Tech",
        "Capítulo Técnico IEEE": "SSIT, ComSoc, IES, IAS",
        "Grupo No Técnico / Afinidad": "SIGHT",
        "Empresa / Aliado Externo": "Ethereum Ecuador (ETH EC), Proyectos ReFi",
        "Rol del Aliado / Notas": "Casos de éxito en trazabilidad y reciclaje"
    },
    {
        "Día": "Día 1",
        "Hora": "16:30 - 17:15",
        "Actividad / Tema": "Panel: Equipos resilientes e innovadores",
        "Eje": "Engage",
        "Capítulo Técnico IEEE": "TEMS",
        "Grupo No Técnico / Afinidad": "YP, WIE",
        "Empresa / Aliado Externo": "Líderes de People Ops / RRHH de Startups Tech",
        "Rol del Aliado / Notas": "Panelistas invitados"
    },
    {
        "Día": "Día 1",
        "Hora": "17:15 - 18:00",
        "Actividad / Tema": "Sesión IEEE CS: Beneficios y oportunidades globales",
        "Eje": "Lead",
        "Capítulo Técnico IEEE": "CS (UTN, Yachay, UPS), IEEE CS Ecuador",
        "Grupo No Técnico / Afinidad": "SAC, YP",
        "Empresa / Aliado Externo": "IEEE Region 9",
        "Rol del Aliado / Notas": "Atracción de miembros e incentivos"
    },
    {
        "Día": "Día 1-2",
        "Hora": "18:00 - 17:00",
        "Actividad / Tema": "Hackatón: Tech for Sustainability Challenge (Fases 1, 2 y 3)",
        "Eje": "Unique",
        "Capítulo Técnico IEEE": "CIS (Jorge Párraga), ComSoc (Román Lara), IES (Mario), IAS",
        "Grupo No Técnico / Afinidad": "YP, SIGHT",
        "Empresa / Aliado Externo": "Ethereum Ecuador (ETH EC), GDG Ecuador, CONQUITO",
        "Rol del Aliado / Notas": "Mentores de código/negocio y Jurado"
    }
]

df_actividades = pd.DataFrame(data_actividades)

# Data 2: Directorio de Líderes y Aliados
data_contactos = [
    {"Nombre / Entidad": "Jorge Párraga", "Rol / Organización": "Presidente CIS Ecuador", "Tipo": "Capítulo Técnico IEEE", "Contacto / Email": "-", "Aporte / Área de Gestión": "IA, Machine Learning, Mentores/Jurado Hackatón"},
    {"Nombre / Entidad": "Román Lara", "Rol / Organización": "Presidente ComSoc Ecuador", "Tipo": "Capítulo Técnico IEEE", "Contacto / Email": "-", "Aporte / Área de Gestión": "Telecomunicaciones, Green IT, Jurado Hackatón"},
    {"Nombre / Entidad": "Mario", "Rol / Organización": "Presidente IES UPS Quito", "Tipo": "Capítulo Técnico IEEE", "Contacto / Email": "-", "Aporte / Área de Gestión": "Electrónica Industrial, Green IT, Gestión Sede Quito"},
    {"Nombre / Entidad": "Ismael Cifuentes", "Rol / Organización": "Coordinación / Miembro IEEE", "Tipo": "Nacional / Sección", "Contacto / Email": "-", "Aporte / Área de Gestión": "Enlace institucional y articulación con capítulos"},
    {"Nombre / Entidad": "Pablo José Robalino Lucero", "Rol / Organización": "IEEE UTN SB Webmaster", "Tipo": "Organizador TechX", "Contacto / Email": "+593 99 528 1793 / pablorobalino@ieee.org", "Aporte / Área de Gestión": "Coordinación General / Web"},
    {"Nombre / Entidad": "Cinthya Catalina Ramón Morocho", "Rol / Organización": "IEEE SIGHT UPS Cuenca Chair", "Tipo": "Organizador TechX", "Contacto / Email": "+593 96 956 7490 / cramonm1@ieee.org", "Aporte / Área de Gestión": "Coordinación General / Proyectos Sostenibles"},
    {"Nombre / Entidad": "Elvis Yangari", "Rol / Organización": "IEEE CS Yachay Tech Chair", "Tipo": "Organizador TechX", "Contacto / Email": "+593 99 946 7353 / elvisy@ieee.org", "Aporte / Área de Gestión": "Coordinación General / Yachay Tech"},
    {"Nombre / Entidad": "Ethereum Ecuador (ETH EC)", "Rol / Organización": "Comunidad Blockchain", "Tipo": "Aliado Externo", "Contacto / Email": "ethereum-ecuador.org", "Aporte / Área de Gestión": "Speakers Blockchain, Mentores, Posible Sponsor"},
    {"Nombre / Entidad": "Google Developer Groups (GDG)", "Rol / Organización": "Comunidad Tech", "Tipo": "Aliado Externo", "Contacto / Email": "-", "Aporte / Área de Gestión": "Speakers IA, Difusión, Swag/Sponsorship"},
    {"Nombre / Entidad": "CONQUITO / Cámara de Comercio Quito", "Rol / Organización": "Agencia de Desarrollo / GAD", "Tipo": "Aliado Institucional", "Contacto / Email": "-", "Aporte / Área de Gestión": "Vinculación PYMEs, Sede, Auspicio Institucional"}
]

df_contactos = pd.DataFrame(data_contactos)

# Data 3: Presupuesto
data_presupuesto = [
    {"Rubro": "Comida y Bebidas", "Detalle": "Almuerzos y coffee breaks (2 días, 150 personas prom.)", "Costo Estimado (USD)": 2500},
    {"Rubro": "Ponentes", "Detalle": "Tokens de agradecimiento y apoyo logístico", "Costo Estimado (USD)": 700},
    {"Rubro": "Competencia", "Detalle": "Premios y certificados para la Hackatón", "Costo Estimado (USD)": 100},
    {"Rubro": "Materiales y Merchandising", "Detalle": "Kits, camisetas, stickers, credenciales", "Costo Estimado (USD)": 300},
    {"Rubro": "Otros", "Detalle": "Decoración, soporte técnico y audiovisual", "Costo Estimado (USD)": 400},
    {"Rubro": "TOTAL PRESUPUESTO", "Detalle": "Monto Total Requerido", "Costo Estimado (USD)": 4000},
    {"Rubro": "Autofinanciamiento", "Detalle": "Aportes locales / Registro / Sponsors externos", "Costo Estimado (USD)": 2500},
    {"Rubro": "Financiamiento IEEE", "Detalle": "Monto solicitado a IEEE CS / Sección Ecuador / R9", "Costo Estimado (USD)": 1500}
]

df_presupuesto = pd.DataFrame(data_presupuesto)

# Exporting to Excel file
file_name = "TechX_EC_Propuesta_Aliados_Programa.xlsx"

with pd.ExcelWriter(file_name, engine="openpyxl") as writer:
    df_actividades.to_excel(writer, sheet_name="Mapeo_Temas_Aliados", index=False)
    df_contactos.to_excel(writer, sheet_name="Directorio_Contactos", index=False)
    df_presupuesto.to_excel(writer, sheet_name="Presupuesto", index=False)

print(f"Archivo generado: {file_name}")